package de.metas.freightcost;

import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.Assert.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import org.adempiere.model.I_M_FreightCost;
import org.adempiere.model.I_M_FreightCostDetail;
import org.adempiere.model.I_M_FreightCostShipper;
import org.adempiere.test.AdempiereTestHelper;
import org.compiere.model.I_C_BP_Group;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.I_C_Country;
import org.compiere.model.I_C_Currency;
import org.compiere.model.I_C_Location;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_PriceList;
import org.compiere.model.I_M_PriceList_Version;
import org.compiere.model.I_M_PricingSystem;
import org.compiere.model.I_M_Product;
import org.compiere.model.I_M_ProductPrice;
import org.compiere.model.I_M_Shipper;
import org.compiere.model.X_C_Order;
import org.junit.Before;
import org.junit.Test;

import de.metas.bpartner.service.IBPartnerBL;
import de.metas.bpartner.service.impl.BPartnerBL;
import de.metas.document.engine.IDocumentBL;
import de.metas.freighcost.FreightCostRepository;
import de.metas.freighcost.FreightCostService;
import de.metas.interfaces.I_C_BPartner;
import de.metas.order.IOrderDAO;
import de.metas.order.OrderFreightCostsService;
import de.metas.user.UserRepository;
import de.metas.util.Services;
import de.metas.util.time.FixedTimeSource;
import de.metas.util.time.SystemTime;

/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2019 metas GmbH
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

public class FreightCostTest
{
	private OrderFreightCostsService orderFreightCostsService;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		final FreightCostRepository freightCostRepo = new FreightCostRepository();
		final FreightCostService freightCostService = new FreightCostService(freightCostRepo);
		orderFreightCostsService = new OrderFreightCostsService(freightCostService);

		Services.registerService(IBPartnerBL.class, new BPartnerBL(new UserRepository()));

		SystemTime.setTimeSource(new FixedTimeSource(2019, 7, 10, 16, 11, 23));
	}

	@Test
	public void orderWithFreightCost_FreightAmt()
	{

		final I_C_BP_Group bpGroup = createBPGroup("BPGroup");

		final I_C_BPartner shipperPartner = createPartner("ShipperPartner", bpGroup.getC_BP_Group_ID());

		final I_M_Shipper shipper1 = createShipper("Shipper1", shipperPartner.getC_BPartner_ID());

		final I_C_BPartner partner1 = createPartner("Partner1", bpGroup.getC_BP_Group_ID());
		partner1.setM_Shipper_ID(shipper1.getM_Shipper_ID());
		save(partner1);

		final I_C_Currency currency1 = createCurrency("Currency1");

		final I_C_Country country1 = createCountry("Country1", currency1.getC_Currency_ID());

		final I_C_BPartner_Location location1 = createBPartnerLocation(partner1.getC_BPartner_ID(), country1.getC_Country_ID());

		final I_C_UOM uom1 = createUOM("uom1");
		final I_M_Product product1 = createProduct("Product1", uom1.getC_UOM_ID());

		final BigDecimal freightAmt = new BigDecimal(123);

		final I_C_Order order1 = createOrder(
				partner1.getC_BPartner_ID(),
				location1.getC_BPartner_Location_ID(),
				X_C_Order.FREIGHTCOSTRULE_FixPrice,
				freightAmt,
				product1.getM_Product_ID(),
				uom1.getC_UOM_ID(),
				currency1.getC_Currency_ID());

		final I_M_Product freightCostProduct = createProduct("FreightCostProduct", uom1.getC_UOM_ID());

		final I_M_FreightCost freightCost = createFreightCost(freightCostProduct.getM_Product_ID(), "FreightCost1");

		final Timestamp validForm = SystemTime.asDayTimestamp();

		final I_M_FreightCostShipper freightCostShipper = createFreightCostShipper(freightCost.getM_FreightCost_ID(),
				shipper1.getM_Shipper_ID(),
				validForm);

		createFreightCostDetail(country1.getC_Country_ID(), freightCostShipper.getM_FreightCostShipper_ID());

		orderFreightCostsService.addFreightRateLineIfNeeded(order1);

		Services.get(IDocumentBL.class).processIt(order1, X_C_Order.DOCACTION_Complete);

		assertThat(order1.getFreightAmt(), comparesEqualTo(freightAmt));

		final List<de.metas.interfaces.I_C_OrderLine> orderLines = Services.get(IOrderDAO.class).retrieveOrderLines(order1);

		assertThat(orderLines.size(), comparesEqualTo(2));

		final de.metas.interfaces.I_C_OrderLine productOrderLine = orderLines.get(0);
		assertThat(productOrderLine.getM_Product_ID(), comparesEqualTo(product1.getM_Product_ID()));

		final de.metas.interfaces.I_C_OrderLine freightCostLine = orderLines.get(1);

		assertThat(freightCostLine.getM_Product_ID(), comparesEqualTo(freightCostProduct.getM_Product_ID()));
		assertThat(freightCostLine.getPriceActual(), comparesEqualTo(freightAmt));

	}

	@Test
	public void orderWithFreightCost_FreightAmtFromPricelist()
	{

		final I_C_BP_Group bpGroup = createBPGroup("BPGroup");

		final I_C_BPartner shipperPartner = createPartner("ShipperPartner", bpGroup.getC_BP_Group_ID());

		final I_M_Shipper shipper1 = createShipper("Shipper1", shipperPartner.getC_BPartner_ID());

		final I_C_BPartner partner1 = createPartner("Partner1", bpGroup.getC_BP_Group_ID());
		partner1.setM_Shipper_ID(shipper1.getM_Shipper_ID());
		save(partner1);

		final I_C_Currency currency1 = createCurrency("Currency1");

		final I_C_Country country1 = createCountry("Country1", currency1.getC_Currency_ID());

		final I_C_BPartner_Location location1 = createBPartnerLocation(partner1.getC_BPartner_ID(), country1.getC_Country_ID());

		final I_C_UOM uom1 = createUOM("uom1");
		final I_M_Product product1 = createProduct("Product1", uom1.getC_UOM_ID());

		final I_C_Order order1 = createOrder(
				partner1.getC_BPartner_ID(),
				location1.getC_BPartner_Location_ID(),
				X_C_Order.FREIGHTCOSTRULE_FixPrice,
				BigDecimal.ZERO,
				product1.getM_Product_ID(),
				uom1.getC_UOM_ID(),
				currency1.getC_Currency_ID());

		final I_M_Product freightCostProduct = createProduct("FreightCostProduct", uom1.getC_UOM_ID());

		final I_M_PricingSystem pricingSystem = createPricingSystem("PricingSystem");
		order1.setM_PricingSystem_ID(pricingSystem.getM_PricingSystem_ID());

		final BigDecimal freightAmt = new BigDecimal(123);
//
//		final I_M_ProductPrice productPrice = createProductPrice(
//				pricingSystem.getM_PricingSystem_ID(),
//				freightCostProduct.getM_Product_ID(),
//				freightAmt,
//				currency1.getC_Currency_ID(),
//				); TODO

		final I_M_FreightCost freightCost = createFreightCost(freightCostProduct.getM_Product_ID(), "FreightCost1");

		final Timestamp validForm = SystemTime.asDayTimestamp();

		final I_M_FreightCostShipper freightCostShipper = createFreightCostShipper(freightCost.getM_FreightCost_ID(),
				shipper1.getM_Shipper_ID(),
				validForm);

		createFreightCostDetail(country1.getC_Country_ID(), freightCostShipper.getM_FreightCostShipper_ID());

		orderFreightCostsService.addFreightRateLineIfNeeded(order1);

		Services.get(IDocumentBL.class).processIt(order1, X_C_Order.DOCACTION_Complete);

		assertThat(order1.getFreightAmt(), comparesEqualTo(freightAmt));

		final List<de.metas.interfaces.I_C_OrderLine> orderLines = Services.get(IOrderDAO.class).retrieveOrderLines(order1);

		assertThat(orderLines.size(), comparesEqualTo(2));

		final de.metas.interfaces.I_C_OrderLine productOrderLine = orderLines.get(0);
		assertThat(productOrderLine.getM_Product_ID(), comparesEqualTo(product1.getM_Product_ID()));

		final de.metas.interfaces.I_C_OrderLine freightCostLine = orderLines.get(1);

		assertThat(freightCostLine.getM_Product_ID(), comparesEqualTo(freightCostProduct.getM_Product_ID()));
		assertThat(freightCostLine.getPriceActual(), comparesEqualTo(freightAmt));

	}

	private I_M_ProductPrice createProductPrice(final int pricingSystemId,
			final int productId,
			final BigDecimal price,
			final int currencyId,
			final int taxCategoryId)
	{
		final I_M_PriceList priceList = newInstance(I_M_PriceList.class);
		priceList.setM_PricingSystem_ID(pricingSystemId);
		priceList.setC_Currency_ID(currencyId);
		save(priceList);

		final I_M_PriceList_Version pricelistVersion = newInstance(I_M_PriceList_Version.class);
		pricelistVersion.setM_PriceList_ID(priceList.getM_PriceList_ID());
		pricelistVersion.setValidFrom(SystemTime.asDayTimestamp());
		save(pricelistVersion);

		final I_M_ProductPrice productPrice = newInstance(I_M_ProductPrice.class);
		productPrice.setM_PriceList_Version_ID(pricelistVersion.getM_PriceList_Version_ID());
		productPrice.setM_Product_ID(productId);
		productPrice.setC_TaxCategory_ID(taxCategoryId);
		productPrice.setPriceStd(price);

		save(productPrice);

		return productPrice;

	}

	private I_M_PricingSystem createPricingSystem(final String name)
	{
		final I_M_PricingSystem pricingSystem = newInstance(I_M_PricingSystem.class);
		pricingSystem.setName(name);
		pricingSystem.setValue(name);

		save(pricingSystem);

		return pricingSystem;
	}

	private I_C_BP_Group createBPGroup(final String name)
	{
		final I_C_BP_Group bpGroup = newInstance(I_C_BP_Group.class);
		bpGroup.setName(name);

		save(bpGroup);

		return bpGroup;
	}

	private I_M_FreightCostShipper createFreightCostShipper(final int freightCostId, final int shipperId, final Timestamp validFrom)
	{
		final I_M_FreightCostShipper freightCostShipper = newInstance(I_M_FreightCostShipper.class);

		freightCostShipper.setM_FreightCost_ID(freightCostId);
		freightCostShipper.setM_Shipper_ID(shipperId);
		freightCostShipper.setValidFrom(validFrom);

		save(freightCostShipper);

		return freightCostShipper;

	}

	private I_M_FreightCostDetail createFreightCostDetail(final int countryId, final int freightCostShipperId)
	{
		final I_M_FreightCostDetail freightCostDetail = newInstance(I_M_FreightCostDetail.class);
		freightCostDetail.setC_Country_ID(countryId);
		freightCostDetail.setM_FreightCostShipper_ID(freightCostShipperId);

		save(freightCostDetail);

		return freightCostDetail;
	}

	private I_M_FreightCost createFreightCost(final int freightCostProductId, final String name)
	{
		final I_M_FreightCost freightCost = newInstance(I_M_FreightCost.class);
		freightCost.setM_Product_ID(freightCostProductId);
		freightCost.setName(name);
		freightCost.setValue(name);
		freightCost.setIsDefault(true);

		save(freightCost);

		return freightCost;
	}

	private I_C_Currency createCurrency(final String name)
	{
		final I_C_Currency currency = newInstance(I_C_Currency.class);
		currency.setCurSymbol(name);

		save(currency);

		return currency;
	}

	private I_C_Country createCountry(final String countryName, final int currencyId)
	{
		final I_C_Country country = newInstance(I_C_Country.class);
		country.setName(countryName);
		country.setC_Currency_ID(currencyId);

		save(country);

		return country;
	}

	private I_C_BPartner_Location createBPartnerLocation(final int partnerId, final int countryId)
	{
		final I_C_Location location = newInstance(I_C_Location.class);
		location.setC_Country_ID(countryId);
		location.setAddress1("Address1");

		save(location);

		final I_C_BPartner_Location bpLocation = newInstance(I_C_BPartner_Location.class);
		bpLocation.setC_Location_ID(location.getC_Location_ID());
		bpLocation.setC_BPartner_ID(partnerId);
		bpLocation.setIsBillTo(true);
		bpLocation.setIsShipTo(true);

		save(bpLocation);

		return bpLocation;
	}

	private I_M_Shipper createShipper(final String shipperName, final int bpartnerId)
	{
		final I_M_Shipper shipper = newInstance(I_M_Shipper.class);
		shipper.setName(shipperName);
		shipper.setC_BPartner_ID(bpartnerId);

		save(shipper);
		return shipper;
	}

	private I_C_BPartner createPartner(final String partnerName, final int bpGroupId)
	{
		final I_C_BPartner partner = newInstance(I_C_BPartner.class);
		partner.setName(partnerName);
		partner.setC_BP_Group_ID(bpGroupId);

		save(partner);

		return partner;
	}

	private I_C_Order createOrder(final int partnerId,
			final int locationId,
			final String freightCostRule,
			final BigDecimal freightAmt,
			final int productId,
			final int uomId,
			final int currencyId)
	{
		final I_C_Order order = newInstance(I_C_Order.class);

		order.setC_BPartner_ID(partnerId);
		order.setC_BPartner_Location_ID(locationId);
		order.setFreightCostRule(freightCostRule);
		order.setDeliveryRule(X_C_Order.DELIVERYVIARULE_Shipper);
		order.setFreightAmt(freightAmt);
		order.setC_Currency_ID(currencyId);
		order.setDateOrdered(SystemTime.asDayTimestamp());

		save(order);

		final I_C_OrderLine orderLine = newInstance(I_C_OrderLine.class);

		orderLine.setM_Product_ID(productId);
		orderLine.setC_UOM_ID(uomId);
		orderLine.setC_Order_ID(order.getC_Order_ID());

		orderLine.setPriceEntered(BigDecimal.TEN);
		orderLine.setQtyEntered(BigDecimal.TEN);

		save(orderLine);

		return order;
	}

	private I_C_UOM createUOM(final String uomName)
	{
		final I_C_UOM uom = newInstance(I_C_UOM.class);

		uom.setName(uomName);
		uom.setUOMSymbol(uomName);

		save(uom);

		return uom;
	}

	private I_M_Product createProduct(final String productName, int uomId)
	{
		final I_M_Product product = newInstance(I_M_Product.class);
		product.setName(productName);
		product.setValue(productName);
		product.setC_UOM_ID(uomId);

		save(product);

		return product;
	}

}
