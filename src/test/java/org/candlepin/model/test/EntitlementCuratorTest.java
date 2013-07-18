/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EntitlementCuratorTest
 */
public class EntitlementCuratorTest extends DatabaseTestFixture {
    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    private EntitlementCertificate firstCertificate;
    private EntitlementCertificate secondCertificate;
    private Owner owner;
    private Consumer consumer;
    private Environment environment;
    private Date overlappingDate;
    private Date futureDate;
    private Date pastDate;
    private Product parentProduct;
    private Product providedProduct1;
    private Product providedProduct2;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        environment = new Environment("env1", "Env 1", owner);
        envCurator.create(environment);

        consumer = createConsumer(owner);
        consumer.setEnvironment(environment);
        consumerCurator.create(consumer);

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool firstPool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(firstPool);

        firstCertificate = createEntitlementCertificate("key", "certificate");

        firstEntitlement = createEntitlement(owner, consumer, firstPool,
            firstCertificate);
        entitlementCurator.create(firstEntitlement);

        Product product1 = TestUtil.createProduct();
        productCurator.create(product1);

        Pool secondPool = createPoolAndSub(owner, product1, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(secondPool);

        secondCertificate = createEntitlementCertificate("key", "certificate");

        secondEntitlement = createEntitlement(owner, consumer, secondPool,
            secondCertificate);
        entitlementCurator.create(secondEntitlement);

        overlappingDate = createDate(2010, 2, 1);
        futureDate = createDate(2050, 1, 1);
        pastDate = createDate(1998, 1, 1);

        parentProduct = TestUtil.createProduct();
        providedProduct1 = TestUtil.createProduct();
        providedProduct2 = TestUtil.createProduct();
        productCurator.create(parentProduct);
        productCurator.create(providedProduct1);
        productCurator.create(providedProduct2);
    }

    private Date createDate(int year, int month, int day) {
        return TestUtil.createDate(year, month, day);
    }

    private Entitlement setupListProvidingEntitlement() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2011, 1, 1);
        Pool testPool = createPoolAndSub(owner, parentProduct, 1L,
            startDate, endDate);

        // Add some provided products to this pool:
        ProvidedProduct p1 = new ProvidedProduct(providedProduct1.getId(),
            providedProduct1.getName());
        ProvidedProduct p2 = new ProvidedProduct(providedProduct2.getId(),
            providedProduct2.getName());
        testPool.addProvidedProduct(p1);
        testPool.addProvidedProduct(p2);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        return ent;
    }

    @Test
    public void listProviding() {
        Entitlement ent = setupListProvidingEntitlement();
        // Test a successful query:
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
                ent.getPool().getProductId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingProvidedProduct() {
        Entitlement ent = setupListProvidingEntitlement();
        // Test a successful query:
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
                providedProduct1.getId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }


    @Test
    public void listProvidingNoResults() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            "nosuchproductid", ent.getStartDate(), ent.getEndDate());
        assertEquals(0, results.size());
    }

    @Test
    public void listProvidingStartDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), overlappingDate, futureDate);
        assertEquals(1, results.size());

    }

    @Test
    public void listProvidingEndDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), pastDate, overlappingDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingTotalOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), pastDate, futureDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingNoOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), pastDate, pastDate);
        assertEquals(0, results.size());
    }

    private Product createModifyingProduct(String modifiedProductId) {
        Product modifierProd = TestUtil.createProduct();
        String randomString = "" + TestUtil.randomInt();
        Content modContent = new Content(randomString, randomString,
            randomString, "type", "somebody", "", "", "");
        Set<String> modifiedProdIds = new HashSet<String>();
        modifiedProdIds.add(modifiedProductId);
        modContent.setModifiedProductIds(modifiedProdIds);
        modifierProd.addContent(modContent);
        contentCurator.create(modContent);
        productCurator.create(modifierProd);
        return modifierProd;
    }

    private Entitlement setupListModifyingEntitlement() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2011, 1, 1);

        Product parentModifierProd = createModifyingProduct(parentProduct.getId());
        Product childModifierProd = createModifyingProduct(providedProduct1.getId());

        Pool testPool = createPoolAndSub(owner, parentModifierProd, 1L,
            startDate, endDate);

        // Add some provided products to this pool which also modify something:
        ProvidedProduct p1 = new ProvidedProduct(childModifierProd.getId(),
            childModifierProd.getName());
        p1.setPool(testPool);
        testPool.addProvidedProduct(p1);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        return ent;
    }

    @Test
    public void listModifying() {
        Entitlement ent = setupListModifyingEntitlement();
        List<Entitlement> results = entitlementCurator.listModifying(consumer,
                parentProduct.getId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }

    @Test
    public void listModifyingProvided() {
        Entitlement ent = setupListModifyingEntitlement();
        List<Entitlement> results = entitlementCurator.listModifying(consumer,
                providedProduct1.getId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }

    @Test
    public void listModifyingNoResults() {
        Entitlement ent = setupListModifyingEntitlement();
        List<Entitlement> results = entitlementCurator.listModifying(consumer,
                "notarealproduct", ent.getStartDate(), ent.getEndDate());
        assertEquals(0, results.size());
    }

    @Test
    public void listModifyingStartDateOverlap() {
        setupListModifyingEntitlement();
        List<Entitlement> results = entitlementCurator.listModifying(consumer,
                parentProduct.getId(), pastDate, overlappingDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listModifyingEndDateOverlap() {
        setupListModifyingEntitlement();
        List<Entitlement> results = entitlementCurator.listModifying(consumer,
                parentProduct.getId(), overlappingDate, futureDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listModifyingTotalOverlap() {
        setupListModifyingEntitlement();
        List<Entitlement> results = entitlementCurator.listModifying(consumer,
                parentProduct.getId(), overlappingDate, overlappingDate);
        assertEquals(1, results.size());
    }

    @Test
    public void shouldReturnCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(secondCertificate.getSerial().getId());
        assertEquals(secondEntitlement, e);
    }

    @Test
    public void shouldReturnInCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(firstCertificate.getSerial().getId());
        assertNotSame(secondEntitlement, e);
    }

    @Test
    public void listForConsumerOnDate() {
        List<Entitlement> ents = entitlementCurator.listByConsumerAndDate(
            consumer, createDate(2015, 1, 1));
        assertEquals(2, ents.size());
    }

    @Test
    public void listByEnvironment() {
        List<Entitlement> ents = entitlementCurator.listByEnvironment(
            environment);
        assertEquals(2, ents.size());
    }

    @Test
    public void testListByConsumerAndProduct() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), req);
        assertEquals(Integer.valueOf(10), page.getMaxRecords());

        List<Entitlement> ents = page.getPageData();
        assertEquals(10, ents.size());

        // Make sure we have the real PageRequest, not the dummy one we send in
        // with the order and sortBy fields.
        assertEquals(req, page.getPageRequest());

        // Check that we've sorted ascending on the id
        for (int i = 0; i < ents.size(); i++) {
            if (i < ents.size() - 1) {
                assertTrue(ents.get(i).getId().compareTo(ents.get(i + 1).getId()) < 1);
            }
        }
    }

    @Test
    public void testListByConsumerAndProductWithoutPaging() {
        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool2);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent2 = createEntitlement(owner, consumer, pool2, cert);
            entitlementCurator.create(ent2);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), null);
        assertEquals(Integer.valueOf(10), page.getMaxRecords());

        List<Entitlement> ents = page.getPageData();
        assertEquals(10, ents.size());

        assertNull(page.getPageRequest());
    }

    @Test
    public void testListByConsumerAndProductFiltered() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool2);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool2, cert);
            entitlementCurator.create(ent);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), req);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(5, page.getPageData().size());
    }


    @Test
    public void listOtherEntsInStack() {
        Product virtProduct = TestUtil.createProduct();
        virtProduct.setAttribute("virt_limit", "4");
        virtProduct.setAttribute("stacking_id", "123");
        productCurator.create(virtProduct);

        Pool pool = createPoolAndSub(owner, virtProduct, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        Pool pool2 = createPoolAndSub(owner, virtProduct, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(3055, 3, 2));
        poolCurator.create(pool2);

        Consumer consumer1 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer1.getType());
        consumerCurator.create(consumer1);

        Consumer consumer2 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer2.getType());
        consumerCurator.create(consumer2);

        // Give first consumer an entitlement to each pool:
        Entitlement ent1 = new Entitlement(pool, consumer1, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(ent1);
        Entitlement ent2 = new Entitlement(pool2, consumer1, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(ent2);

        // Give another a consumer an entitlement to one of the pools so we can be sure
        // it's filtered out:
        Entitlement otherEnt = new Entitlement(pool, consumer2, pool.getStartDate(),
            pool.getEndDate(), 1);

        List<Entitlement> ents = entitlementCurator.listEntsForReSource("123",
            ent1);
        assertEquals(1, ents.size());
        assertEquals(ent2.getId(), ents.get(0).getId());

        ents = entitlementCurator.listEntsForReSource("123", ent2);
        assertEquals(1, ents.size());
        assertEquals(ent1.getId(), ents.get(0).getId());

        ents = entitlementCurator.listEntsForReSource("123", otherEnt);
        assertEquals(0, ents.size());
    }

    @Test
    public void listOtherEntsInStackIgnoresDerived() {
        Product virtProduct = TestUtil.createProduct();
        virtProduct.setAttribute("virt_limit", "4");
        virtProduct.setAttribute("stacking_id", "123");
        productCurator.create(virtProduct);

        Consumer consumer1 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer1.getType());
        consumerCurator.create(consumer1);

        Pool pool = createPoolAndSub(owner, virtProduct, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(2055, 3, 2));
        poolCurator.create(pool);
        Entitlement ent1 = new Entitlement(pool, consumer1, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(ent1);

        // Second pool is "derived":
        Pool pool2 = createPoolAndSub(owner, virtProduct, 1L,
            TestUtil.createDate(2011, 3, 2), TestUtil.createDate(3055, 3, 2));
        pool2.setSourceEntitlement(ent1);
        poolCurator.create(pool2);

        Entitlement ent2 = new Entitlement(pool2, consumer1, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(ent2);

        List<Entitlement> ents = entitlementCurator.listEntsForReSource("123",
            ent1);
        assertEquals(0, ents.size());
    }

    @Test
    public void listOtherEntsInStackPrefersActiveEntitlements() {
        Consumer consumer = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        Product virtProduct = TestUtil.createProduct();
        virtProduct.setAttribute("virt_limit", "4");
        virtProduct.setAttribute("stacking_id", "123");
        productCurator.create(virtProduct);

        // Initial Entitlement
        Entitlement initial = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 2), createFutureDate(Calendar.MONTH, 3));

        // Expired entitlement
        createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 10), createPastDate(Calendar.MONTH, 6));

        // Active entitlement
        Entitlement expected = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 8), createFutureDate(Calendar.MONTH, 5));

        List<Entitlement> ents = entitlementCurator.listEntsForReSource("123", initial);
        assertEquals(1, ents.size());
        assertEquals(expected.getId(), ents.get(0).getId());
    }

    @Test
    public void listOtherEntsInStackPreferActiveWithGreatestEndDate() {
        Consumer consumer = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        Product virtProduct = TestUtil.createProduct();
        virtProduct.setAttribute("virt_limit", "4");
        virtProduct.setAttribute("stacking_id", "123");
        productCurator.create(virtProduct);

        // Initial Entitlement
        Entitlement initial = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 2), createFutureDate(Calendar.MONTH, 3));

        // Active entitlement - ends sooner
        Entitlement endsSooner = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 2), createFutureDate(Calendar.MONTH, 3));

        // Active entitlement - ends later
        Entitlement endsLater = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 2), createFutureDate(Calendar.MONTH, 6));

        List<Entitlement> ents = entitlementCurator.listEntsForReSource("123", initial);
        assertEquals(2, ents.size());
        assertEquals(endsLater.getId(), ents.get(0).getId());
        assertEquals(endsSooner.getId(), ents.get(1).getId());
    }

    @Test
    public void listOtherEntsInStackPreferFutureEntsWhenNoActiveEntExists() {
        Consumer consumer = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        Product virtProduct = TestUtil.createProduct();
        virtProduct.setAttribute("virt_limit", "4");
        virtProduct.setAttribute("stacking_id", "123");
        productCurator.create(virtProduct);

        // Initial Entitlement
        Entitlement initial = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 2), createFutureDate(Calendar.MONTH, 3));

        // Expired entitlement
        createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 10), createPastDate(Calendar.MONTH, 6));

        // Future entitlement
        Entitlement expected = createEntitlement(consumer, virtProduct,
            createFutureDate(Calendar.MONTH, 8), createFutureDate(Calendar.MONTH, 12));

        List<Entitlement> ents = entitlementCurator.listEntsForReSource("123", initial);
        assertEquals(1, ents.size());
        assertEquals(expected.getId(), ents.get(0).getId());
    }

    @Test
    public void listOtherEntsInStackPreferFutureEntThatBecomesActiveSoonest() {
        Consumer consumer = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);

        Product virtProduct = TestUtil.createProduct();
        virtProduct.setAttribute("virt_limit", "4");
        virtProduct.setAttribute("stacking_id", "123");
        productCurator.create(virtProduct);

        // Initial Entitlement
        Entitlement initial = createEntitlement(consumer, virtProduct,
            createPastDate(Calendar.MONTH, 2), createFutureDate(Calendar.MONTH, 3));

        // Future entitlement - starts sooner
        Entitlement startsSooner = createEntitlement(consumer, virtProduct,
            createFutureDate(Calendar.MONTH, 4), createFutureDate(Calendar.MONTH, 6));

        // Future entitlement - starts later
        Entitlement startsLater = createEntitlement(consumer, virtProduct,
            createFutureDate(Calendar.MONTH, 6), createFutureDate(Calendar.MONTH, 8));

        List<Entitlement> ents = entitlementCurator.listEntsForReSource("123", initial);
        assertEquals(2, ents.size());
        assertEquals(startsSooner.getId(), ents.get(0).getId());
        assertEquals(startsLater.getId(), ents.get(1).getId());
    }

    private Entitlement createEntitlement(Consumer consumer, Product product,
        Date startDate, Date endDate) {
        Pool pool = createPoolAndSub(owner, product, 1L, startDate, endDate);
        poolCurator.create(pool);

        Entitlement ent = new Entitlement(pool, consumer, pool.getStartDate(),
            pool.getEndDate(), 1);
        entitlementCurator.create(ent);
        return ent;
    }

    private Date createPastDate(int calConstant, int unitsInPast) {
        Calendar cal = Calendar.getInstance();
        cal.add(calConstant, -1 * unitsInPast);
        return cal.getTime();
    }

    private Date createFutureDate(int calConstant, int unitsInFuture) {
        Calendar cal = Calendar.getInstance();
        cal.add(calConstant, unitsInFuture);
        return cal.getTime();
    }
}
