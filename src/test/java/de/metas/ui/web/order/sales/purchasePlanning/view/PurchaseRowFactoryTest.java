package de.metas.ui.web.order.sales.purchasePlanning.view;

import static java.math.BigDecimal.ONE;
import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.save;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.temporal.ChronoUnit;

import org.adempiere.bpartner.BPartnerId;
import org.adempiere.service.OrgId;
import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.time.SystemTime;
import org.adempiere.warehouse.WarehouseId;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_UOM;
import org.compiere.model.I_M_Product;
import org.compiere.model.I_M_Product_Category;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import de.metas.ShutdownListener;
import de.metas.StartupListener;
import de.metas.contracts.subscription.model.I_C_OrderLine;
import de.metas.material.dispo.commons.repository.AvailableToPromiseRepository;
import de.metas.money.Currency;
import de.metas.money.Money;
import de.metas.money.MoneyService;
import de.metas.money.grossprofit.GrossProfitPriceFactory;
import de.metas.order.OrderAndLineId;
import de.metas.product.ProductAndCategoryId;
import de.metas.purchasecandidate.PurchaseCandidate;
import de.metas.purchasecandidate.PurchaseCandidateId;
import de.metas.purchasecandidate.PurchaseCandidatesGroup;
import de.metas.purchasecandidate.PurchaseDemandId;
import de.metas.purchasecandidate.VendorProductInfo;
import de.metas.purchasecandidate.grossprofit.PurchaseProfitInfo;
import de.metas.ui.web.window.datatypes.DocumentId;

/*
 * #%L
 * metasfresh-webui-api
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { StartupListener.class, ShutdownListener.class, GrossProfitPriceFactory.class })
public class PurchaseRowFactoryTest
{
	private Currency currency;

	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();

		currency = PurchaseRowTestTools.createCurrency();
	}

	@Test
	public void test()
	{
		final PurchaseCandidate purchaseCandidate = createPurchaseCandidate(30);
		final PurchaseRowFactory purchaseRowFactory = new PurchaseRowFactory(
				new AvailableToPromiseRepository(),
				new MoneyService());

		final PurchaseRow candidateRow = purchaseRowFactory.lineRowBuilder()
				.purchaseCandidatesGroup(PurchaseCandidatesGroup.of(purchaseCandidate))
				.purchaseDemandId(PurchaseDemandId.ofOrderAndLineId(purchaseCandidate.getSalesOrderAndLineId()))
				.datePromised(SystemTime.asLocalDateTime())
				.convertAmountsToCurrency(currency)
				.build();

		final DocumentId id = candidateRow.getId();
		final PurchaseRowId purchaseRowId = PurchaseRowId.fromDocumentId(id);

		assertThat(purchaseRowId.getVendorId()).isEqualTo(purchaseCandidate.getVendorId());
		assertThat(purchaseRowId.getPurchaseDemandId()).isEqualTo(PurchaseDemandId.ofTableAndRecordId(
				I_C_OrderLine.Table_Name,
				purchaseCandidate.getSalesOrderAndLineId().getOrderLineRepoId()));
		assertThat(purchaseRowId.getProcessedPurchaseCandidateId()).isEqualTo(PurchaseCandidateId.ofRepoId(30));

	}

	public PurchaseCandidate createPurchaseCandidate(final int purchaseCandidateId)
	{
		final I_C_BPartner bpartner = newInstance(I_C_BPartner.class);
		save(bpartner);

		final I_C_UOM uom = newInstance(I_C_UOM.class);
		uom.setUOMSymbol("uomSympol");
		save(uom);

		final I_M_Product_Category productCategory = newInstance(I_M_Product_Category.class);
		save(productCategory);

		final I_M_Product product = newInstance(I_M_Product.class);
		product.setC_UOM(uom);
		product.setM_Product_Category_ID(productCategory.getM_Product_Category_ID());
		save(product);
		final ProductAndCategoryId productAndCategoryId = ProductAndCategoryId.of(product.getM_Product_ID(), product.getM_Product_Category_ID());

		final VendorProductInfo vendorProductInfo = VendorProductInfo.builder()
				.vendorId(BPartnerId.ofRepoId(bpartner.getC_BPartner_ID()))
				.productAndCategoryId(productAndCategoryId)
				.vendorProductNo("productNo")
				.vendorProductName("productName")
				.build();

		final PurchaseProfitInfo profitInfo = PurchaseProfitInfo
				.builder()
				.salesNetPrice(Money.of(11, currency))
				.purchaseNetPrice(Money.of(9, currency))
				.purchaseGrossPrice(Money.of(10, currency))
				.build();

		return PurchaseCandidate.builder()
				.id(PurchaseCandidateId.ofRepoIdOrNull(purchaseCandidateId))
				.salesOrderAndLineId(OrderAndLineId.ofRepoIds(1, 2))
				.orgId(OrgId.ofRepoId(3))
				.warehouseId(WarehouseId.ofRepoId(4))
				.vendorId(vendorProductInfo.getVendorId())
				.aggregatePOs(vendorProductInfo.isAggregatePOs())
				.productId(vendorProductInfo.getProductId())
				.uomId(uom.getC_UOM_ID())
				.qtyToPurchase(ONE)
				.dateRequired(SystemTime.asLocalDateTime().truncatedTo(ChronoUnit.DAYS))
				.profitInfo(profitInfo)
				.processed(true) // imporant if we expect purchaseRowId.getProcessedPurchaseCandidateId() to be > 0
				.locked(false)
				.build();
	}

}
