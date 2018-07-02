package de.metas.handlingunits.impl;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.adempiere.model.InterfaceWrapperHelper.saveRecord;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.adempiere.ad.dao.IQueryFilter;
import org.adempiere.ad.wrapper.POJOWrapper;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2015 metas GmbH
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

import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.text.ExtendedReflectionToStringBuilder;
import org.adempiere.util.text.RecursiveIndentedMultilineToStringStyle;
import org.adempiere.warehouse.WarehouseId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import de.metas.adempiere.model.I_M_Product;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_Reservation;
import de.metas.handlingunits.model.I_M_HU_Storage;
import de.metas.handlingunits.model.I_M_Locator;
import de.metas.handlingunits.model.I_M_Warehouse;
import de.metas.handlingunits.model.X_M_HU;
import de.metas.handlingunits.reservation.HUReservationRepository;
import de.metas.order.OrderLineId;

public class HUQueryBuilderTest
{
	private I_M_Warehouse wh;

	private I_M_Product product;
	private List<I_M_HU> hus;

	private HUQueryBuilder huQueryBuilder;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		wh = newInstance(I_M_Warehouse.class);
		save(wh);

		final I_M_Locator locator = newInstance(I_M_Locator.class);
		locator.setM_Warehouse(wh);
		save(locator);

		final I_M_Warehouse otherWh = newInstance(I_M_Warehouse.class);
		save(otherWh);

		final I_M_Locator otherLocator = newInstance(I_M_Locator.class);
		otherLocator.setM_Warehouse(otherWh);
		save(otherLocator);

		product = newInstance(I_M_Product.class);
		save(product);

		final I_M_Product otherProduct = newInstance(I_M_Product.class);
		save(otherProduct);

		hus = ImmutableList.of(
				createHU("locator-product", locator, product),
				createHU("locator-product-nosourceHU", locator, product),
				createHU("locator-otherProduct", locator, otherProduct),
				createHU("otherLocator-product", otherLocator, product),
				createHU("otherLocator-otherProduct", otherLocator, otherProduct));

		huQueryBuilder = new HUQueryBuilder(new HUReservationRepository());
	}

	private static I_M_HU createHU(
			final String instanceName,
			final I_M_Locator locator,
			final I_M_Product product)
	{
		final I_M_HU hu = newInstance(I_M_HU.class);
		hu.setHUStatus(X_M_HU.HUSTATUS_Active);
		hu.setM_Locator(locator);
		save(hu);
		POJOWrapper.setInstanceName(hu, instanceName);

		final I_M_HU_Storage hu_storage = newInstance(I_M_HU_Storage.class);
		hu_storage.setM_HU(hu);
		hu_storage.setM_Product(product);
		save(hu_storage);

		return hu;
	}

	/**
	 * Just makes sure that {@link HUQueryBuilder#copy()} is not failing.
	 */
	@Test
	public void test_copy_NotFails()
	{
		final HUQueryBuilder husQueryCopy = huQueryBuilder.copy();

		Assert.assertNotNull("copy shall not be null", husQueryCopy);
		Assert.assertNotSame("original and copy shall not be the same", husQueryCopy, huQueryBuilder);
		assertSameStringRepresentation(huQueryBuilder, husQueryCopy);
	}

	private final void assertSameStringRepresentation(final Object expected, final Object actual)
	{
		final String expectedStr = toString(expected);
		final String actualStr = toString(actual);

		final String message = "String representations shall be the same"
				+ "\nExpected: " + expectedStr
				+ "\nActual: " + actualStr;
		Assert.assertEquals(message, expectedStr, actualStr);
	}

	private final String toString(final Object obj)
	{
		return new ExtendedReflectionToStringBuilder(obj, RecursiveIndentedMultilineToStringStyle.instance)
				.toString();
	}

	@Test
	public void createQueryFilter_by_product_and_warehouse()
	{
		huQueryBuilder
				.addOnlyWithProduct(product)
				.addOnlyInWarehouseId(WarehouseId.ofRepoId(wh.getM_Warehouse_ID()));

		// invoke the method under test
		final IQueryFilter<I_M_HU> huFilters = huQueryBuilder.createQueryFilter();

		assertThat(huFilters.accept(hus.get(0))).isTrue();
		assertThat(huFilters.accept(hus.get(1))).isTrue();
		assertThat(huFilters.accept(hus.get(2))).isFalse();
		assertThat(huFilters.accept(hus.get(3))).isFalse();
		assertThat(huFilters.accept(hus.get(4))).isFalse();
	}

	@Test
	public void createQueryFilter_select_all()
	{

		// invoke the method under test
		final IQueryFilter<I_M_HU> huFilters = huQueryBuilder.createQueryFilter();

		assertThat(hus).allMatch(hu -> huFilters.accept(hu));
	}

	@Test
	public void createQueryFilter_exclude_reserved()
	{

		final OrderLineId orderLineId = OrderLineId.ofRepoId(10);
		createReservationRecord(orderLineId, hus.get(0));

		final OrderLineId otherOrderLineId = OrderLineId.ofRepoId(20);
		createReservationRecord(otherOrderLineId, hus.get(1));

		huQueryBuilder.setExcludeReservedToOtherThan(orderLineId);

		// invoke the method under test
		final IQueryFilter<I_M_HU> huFilters = huQueryBuilder.createQueryFilter();

		assertThat(huFilters.accept(hus.get(0))).isTrue(); // because it's reserved for "orderLineId"
		assertThat(huFilters.accept(hus.get(1))).isFalse(); // because it's reserved for a different order line
		assertThat(huFilters.accept(hus.get(2))).isTrue(); // because they are not reserved at all
		assertThat(huFilters.accept(hus.get(3))).isTrue();
		assertThat(huFilters.accept(hus.get(4))).isTrue();
	}

	private void createReservationRecord(final OrderLineId orderLineId, final I_M_HU hu)
	{
		hu.setIsReserved(true);
		saveRecord(hu);
		final I_M_HU_Reservation huReservationRecord = newInstance(I_M_HU_Reservation.class);
		huReservationRecord.setVHU(hu);
		huReservationRecord.setC_OrderLineSO_ID(orderLineId.getRepoId());
		saveRecord(huReservationRecord);
	}

}
