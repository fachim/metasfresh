package de.metas.ui.web.picking.process;

import static org.adempiere.model.InterfaceWrapperHelper.loadOutOfTrx;

import java.util.List;
import java.util.stream.Collectors;

import org.adempiere.util.Services;
import org.compiere.util.Env;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ImmutableList;

import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.picking.IHUPickingSlotBL;
import de.metas.handlingunits.picking.IHUPickingSlotBL.PickingHUsQuery;
import de.metas.inoutcandidate.model.I_M_ShipmentSchedule;
import de.metas.process.IADProcessDAO;
import de.metas.process.ProcessPreconditionsResolution;
import de.metas.process.RelatedProcessDescriptor;
import de.metas.ui.web.handlingunits.HUIdsFilterHelper;
import de.metas.ui.web.handlingunits.WEBUI_HU_Constants;
import de.metas.ui.web.picking.pickingslot.PickingSlotRow;
import de.metas.ui.web.picking.pickingslot.PickingSlotView;
import de.metas.ui.web.process.adprocess.ViewBasedProcessTemplate;
import de.metas.ui.web.view.CreateViewRequest;
import de.metas.ui.web.view.IView;
import de.metas.ui.web.view.IViewsRepository;
import de.metas.ui.web.view.ViewId;
import de.metas.ui.web.view.json.JSONViewDataType;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2017 metas GmbH
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

/**
 * This process opens a HU editor window within the picking window and registers the processes {@link WEBUI_Picking_HUEditor_PickHU} for that window.
 * 
 * @author metas-dev <dev@metasfresh.com>
 *
 */
public class WEBUI_Picking_HUEditor_Launcher extends ViewBasedProcessTemplate
{
	@Autowired
	private IViewsRepository viewsRepo;

	private final transient IADProcessDAO adProcessDAO = Services.get(IADProcessDAO.class);

	private final transient IHUPickingSlotBL huPickingSlotBL = Services.get(IHUPickingSlotBL.class);

	@Override
	protected ProcessPreconditionsResolution checkPreconditionsApplicable()
	{
		if (!getSelectedDocumentIds().isSingleDocumentId())
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}
		return ProcessPreconditionsResolution.accept();
	}

	@Override
	protected String doIt()
	{
		final List<Integer> availableHUsToPickIDs = retrieveAvailableHuIdsForCurrentShipmentScheduleId();

		final IView husToPickView = createHuEditorView(availableHUsToPickIDs);

		getResult().setWebuiIncludedViewIdToOpen(husToPickView.getViewId().getViewId());

		return MSG_OK;
	}

	private List<Integer> retrieveAvailableHuIdsForCurrentShipmentScheduleId()
	{
		final int shipmentScheduleId = getView().getCurrentShipmentScheduleId();

		final PickingHUsQuery query = PickingHUsQuery.builder()
				.shipmentSchedules(ImmutableList.of(loadOutOfTrx(shipmentScheduleId, I_M_ShipmentSchedule.class)))
				.onlyTopLevelHUs(false)
				.onlyIfAttributesMatchWithShipmentSchedules(true)
				.build();
		final List<I_M_HU> availableHUsToPick = huPickingSlotBL.retrieveAvailableHUsToPick(query);
		final List<Integer> availableHUsToPickIDs = availableHUsToPick.stream().map(hu -> hu.getM_HU_ID()).collect(Collectors.toList());
		return availableHUsToPickIDs;
	}

	private IView createHuEditorView(@NonNull final List<Integer> availableHUsToPickIDs)
	{
		final PickingSlotRow pickingSlotRow = getSingleSelectedRow();
		final ViewId pickingSlotViewId = getView().getViewId();

		final IView husToPickView = viewsRepo.createView(
				CreateViewRequest.builder(WEBUI_HU_Constants.WEBUI_HU_Window_ID, JSONViewDataType.includedView)
						.setParentViewId(pickingSlotViewId)
						.setParentRowId(pickingSlotRow.getId())
						.addStickyFilters(HUIdsFilterHelper.createFilter(availableHUsToPickIDs))
						.addAdditionalRelatedProcessDescriptor(createProcessDescriptor(WEBUI_Picking_HUEditor_PickHU.class))
						.addAdditionalRelatedProcessDescriptor(createProcessDescriptor(WEBUI_Picking_HUEditor_Create_M_Source_HUs.class))
						.build());
		return husToPickView;
	}

	private RelatedProcessDescriptor createProcessDescriptor(@NonNull final Class<?> processClass)
	{
		final RelatedProcessDescriptor flagSelectedHUsAsSourceHUs = RelatedProcessDescriptor.builder()
				.processId(adProcessDAO.retriveProcessIdByClassIfUnique(Env.getCtx(), processClass))
				.webuiQuickAction(true)
				.build();
		return flagSelectedHUsAsSourceHUs;
	}

	@Override
	protected PickingSlotView getView()
	{
		return PickingSlotView.cast(super.getView());
	}

	@Override
	protected PickingSlotRow getSingleSelectedRow()
	{
		return PickingSlotRow.cast(super.getSingleSelectedRow());
	}

}
