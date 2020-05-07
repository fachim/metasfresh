package de.metas.ui.web.window.model;

import java.util.Properties;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.adempiere.ad.element.api.AdWindowId;
import org.adempiere.ad.table.api.IADTableDAO;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_AD_Column;
import org.compiere.util.Evaluatee;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;

import de.metas.document.references.IZoomSource;
import de.metas.document.references.ZoomInfo;
import de.metas.document.references.ZoomInfoFactory;
import de.metas.i18n.ITranslatableString;
import de.metas.i18n.TranslatableStrings;
import de.metas.logging.LogManager;
import de.metas.security.IUserRolePermissions;
import de.metas.ui.web.document.filter.provider.userQuery.MQueryDocumentFilterHelper;
import de.metas.ui.web.window.WindowConstants;
import de.metas.ui.web.window.datatypes.DocumentPath;
import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.ui.web.window.descriptor.DocumentEntityDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDataBindingDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDescriptor;
import de.metas.util.Services;
import lombok.Getter;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Service
public class DocumentReferencesService
{
	private static final Logger logger = LogManager.getLogger(DocumentReferencesService.class);
	private final DocumentCollection documentCollection;

	public DocumentReferencesService(@NonNull final DocumentCollection documentCollection)
	{
		this.documentCollection = documentCollection;
	}

	public Stream<DocumentReference> getDocumentReferences(
			@NonNull final DocumentPath documentPath,
			@NonNull final IUserRolePermissions rolePermissions)
	{
		// Document with composed keys does not support references
		if (documentPath.isComposedKey())
		{
			return Stream.empty();
		}

		final ZoomInfoFactory zoomInfoFactory = ZoomInfoFactory.get();

		final Stopwatch stopwatch = Stopwatch.createStarted();
		final Stream<DocumentReference> documentReferences = documentCollection.forDocumentReadonly(
				documentPath,
				document -> {
					if (document.isNew())
					{
						return Stream.empty();
					}

					final ITranslatableString filterCaption = extractFilterCaption(document);

					final DocumentAsZoomSource zoomSource = new DocumentAsZoomSource(document);
					return zoomInfoFactory
							.streamZoomInfos(zoomSource, rolePermissions)
							.map(zoomInfo -> toDocumentReference(zoomInfo, filterCaption));
				});
		stopwatch.stop();

		logger.debug("Fetched initial document references stream for {} in {}", documentPath, stopwatch);

		return documentReferences;
	}

	private static DocumentReference toDocumentReference(
			@NonNull final ZoomInfo zoomInfo,
			@NonNull final ITranslatableString filterCaption)
	{
		final WindowId windowId = WindowId.of(zoomInfo.getAdWindowId());

		// NOTE: we use the windowId as the ID because we want to have only one document reference per window.
		// In case of multiple references, the one with highest priority shall be picked.\\
		final String id = windowId.toJson();

		return DocumentReference.builder()
				.id(id)
				.internalName(zoomInfo.getInternalName())
				.caption(zoomInfo.getLabel())
				.windowId(windowId)
				.priority(zoomInfo.getPriority())
				.documentsCount(zoomInfo.getRecordCount())
				.filter(MQueryDocumentFilterHelper.createDocumentFilterFromMQuery(zoomInfo.getQuery(), filterCaption))
				.loadDuration(zoomInfo.getRecordCountDuration())
				.build();
	}

	public DocumentReference getDocumentReference(
			final DocumentPath sourceDocumentPath,
			final WindowId targetWindowId,
			@NonNull final IUserRolePermissions rolePermissions)
	{
		final ZoomInfoFactory zoomInfoFactory = ZoomInfoFactory.get();

		return documentCollection.forDocumentReadonly(sourceDocumentPath, sourceDocument -> {
			if (sourceDocument.isNew())
			{
				throw new AdempiereException("New documents cannot be referenced: " + sourceDocument);
			}

			final DocumentAsZoomSource zoomSource = new DocumentAsZoomSource(sourceDocument);
			final ZoomInfo zoomInfo = zoomInfoFactory.retrieveZoomInfo(
					zoomSource,
					targetWindowId.toAdWindowId(),
					rolePermissions);

			final ITranslatableString filterCaption = extractFilterCaption(sourceDocument);

			return toDocumentReference(zoomInfo, filterCaption);
		});
	}

	private final ITranslatableString extractFilterCaption(final Document sourceDocument)
	{
		//
		// Window caption
		final ITranslatableString windowCaption = sourceDocument.getEntityDescriptor().getCaption();

		//
		// Document info
		// TODO: i think we shall use lookup to fetch the document description
		final ITranslatableString documentSummary;
		if (sourceDocument.hasField(WindowConstants.FIELDNAME_DocumentSummary))
		{
			final String documentSummaryStr = sourceDocument.getFieldView(WindowConstants.FIELDNAME_DocumentSummary).getValueAs(String.class);
			documentSummary = TranslatableStrings.constant(documentSummaryStr);
		}
		else if (sourceDocument.hasField(WindowConstants.FIELDNAME_DocumentNo))
		{
			final String documentNoStr = sourceDocument.getFieldView(WindowConstants.FIELDNAME_DocumentNo).getValueAs(String.class);
			documentSummary = TranslatableStrings.constant(documentNoStr);
		}
		else if (sourceDocument.hasField(WindowConstants.FIELDNAME_Name))
		{
			final String nameStr = sourceDocument.getFieldView(WindowConstants.FIELDNAME_Name).getValueAs(String.class);
			documentSummary = TranslatableStrings.constant(nameStr);
		}
		else
		{
			documentSummary = TranslatableStrings.constant(sourceDocument.getDocumentId().toString());
		}

		// Window caption + document info
		return TranslatableStrings.join(" ", windowCaption, documentSummary);
	}

	private static final class DocumentAsZoomSource implements IZoomSource
	{
		private final Properties ctx;
		private final Evaluatee evaluationContext;

		private final AdWindowId adWindowId;
		private final int adTableId;
		private final int recordId;
		private final String keyColumnName;
		private final Document document;

		@Getter
		private final boolean genericZoomOrigin;

		@Getter
		private final String tableName;

		private DocumentAsZoomSource(@NonNull final Document document)
		{
			ctx = document.getCtx();
			this.document = document;
			evaluationContext = document.asEvaluatee();

			final DocumentEntityDescriptor entityDescriptor = document.getEntityDescriptor();
			adWindowId = entityDescriptor.getWindowId().toAdWindowId();
			tableName = entityDescriptor.getTableName();

			adTableId = Services.get(IADTableDAO.class).retrieveTableId(tableName);
			recordId = document.getDocumentId().toInt();
			keyColumnName = extractSingleKeyColumNameOrNull(entityDescriptor);

			genericZoomOrigin = extractGenericZoomOrigin(tableName, keyColumnName);
		}

		private static String extractSingleKeyColumNameOrNull(final DocumentEntityDescriptor entityDescriptor)
		{
			final DocumentFieldDescriptor idField = entityDescriptor.getSingleIdFieldOrNull();
			if (idField == null)
			{
				return null;
			}

			final DocumentFieldDataBindingDescriptor idFieldBinding = idField.getDataBinding().orElse(null);
			if (idFieldBinding == null)
			{
				return null;
			}

			final String keyColumnName = idFieldBinding.getColumnName();
			return keyColumnName;
		}

		private boolean extractGenericZoomOrigin(
				@NonNull final String tableName,
				@Nullable final String keyColumnName)
		{
			if (keyColumnName != null)
			{
				final IADTableDAO adTableDAO = Services.get(IADTableDAO.class);
				final I_AD_Column idColumn = adTableDAO.retrieveColumn(tableName, keyColumnName);
				return idColumn.isGenericZoomOrigin();
			}
			return false;
		}

		@Override
		public String toString()
		{
			return MoreObjects.toStringHelper(this)
					.add("tableName", tableName)
					.add("recordId", recordId)
					.add("AD_Window_ID", adWindowId)
					.toString();
		}

		@Override
		public Properties getCtx()
		{
			return ctx;
		}

		@Override
		public String getTrxName()
		{
			return ITrx.TRXNAME_ThreadInherited;
		}

		@Override
		public AdWindowId getAD_Window_ID()
		{
			return adWindowId;
		}

		@Override
		public int getAD_Table_ID()
		{
			return adTableId;
		}

		@Override
		public String getKeyColumnNameOrNull()
		{
			return keyColumnName;
		}

		@Override
		public int getRecord_ID()
		{
			return recordId;
		}

		@Override
		public Evaluatee createEvaluationContext()
		{
			return evaluationContext;
		}

		@Override
		public boolean hasField(final String columnName)
		{
			return document.hasField(columnName);
		}

		@Override
		public Object getFieldValue(final String columnName)
		{
			return document.getFieldView(columnName).getValue();
		}

		@Override
		public boolean getFieldValueAsBoolean(final String columnName)
		{
			return document.getFieldView(columnName).getValueAsBoolean();
		}
	}
}
