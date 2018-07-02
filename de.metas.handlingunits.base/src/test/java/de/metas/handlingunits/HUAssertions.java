package de.metas.handlingunits;

import javax.annotation.Nullable;

import org.adempiere.ad.dao.ICompositeQueryFilter;
import org.adempiere.ad.dao.IQueryFilter;
import org.assertj.core.api.Assertions;

import de.metas.handlingunits.model.I_M_HU;
import de.metas.testsupport.CompositeQueryFilterAssert;
import de.metas.testsupport.MetasfreshAssertions;
import de.metas.testsupport.ModelAssert;
import de.metas.testsupport.QueryFilterAssert;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2018 metas GmbH
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

@SuppressWarnings("rawtypes")
public class HUAssertions extends Assertions
{
	public static HUAssert assertThat(I_M_HU actual)
	{
		return new HUAssert(actual);
	}

	public static CompositeQueryFilterAssert assertThat(@Nullable final ICompositeQueryFilter actual)
	{
		return MetasfreshAssertions.assertThat(actual);
	}

	public static QueryFilterAssert assertThat(@Nullable final IQueryFilter actual)
	{
		return MetasfreshAssertions.assertThat(actual);
	}

	public static ModelAssert assertThatModel(@Nullable final Object actual)
	{
		return MetasfreshAssertions.assertThatModel(actual);
	}
}
