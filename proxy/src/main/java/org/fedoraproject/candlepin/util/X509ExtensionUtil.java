/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.util;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERUTF8String;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;

/**
 * X509ExtensionUtil
 */
public class X509ExtensionUtil {

    private static Logger log = Logger.getLogger(X509ExtensionUtil.class);
    private SimpleDateFormat iso8601DateFormat;

    public X509ExtensionUtil() {
        //Output everything in UTC
        iso8601DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso8601DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    public Set<X509ExtensionWrapper> consumerExtensions(Consumer consumer) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        // 1.3.6.1.4.1.2312.9.5.1
        // REDHAT_OID here seems wrong...
        String consumerOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.SYSTEM_NAMESPACE_KEY);
        toReturn.add(new X509ExtensionWrapper(consumerOid + "." +
            OIDUtil.SYSTEM_OIDS.get(OIDUtil.UUID_KEY), false,
            new DERUTF8String(consumer.getUuid())));

        return toReturn;
    }

    public Set<X509ExtensionWrapper> subscriptionExtensions(Subscription sub) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();
        // Subscription/order info
        // need the sub product name, not id here
        // NOTE: order ~= subscriptio
        // entitlement == entitlement

        String subscriptionOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        if (sub.getProduct().getId() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NAME_KEY), false,
                new DERUTF8String(sub.getProduct().getId())));
        }
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NUMBER_KEY), false,
            new DERUTF8String(sub.getId().toString())));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_SKU_KEY), false,
            new DERUTF8String(sub.getProduct().getId().toString())));
        // TODO: regnum? virtlimit/socketlimit?
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_QUANTITY_KEY), false,
            new DERUTF8String(sub.getQuantity().toString())));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_STARTDATE_KEY), false,
            new DERUTF8String(iso8601DateFormat.format(sub.getStartDate()))));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_ENDDATE_KEY), false,
            new DERUTF8String(iso8601DateFormat.format(sub.getEndDate()))));
        // TODO : use keys
        String warningPeriod = sub.getProduct().getAttributeValue("warning_period");
        if (warningPeriod == null) {
            warningPeriod = "0";
        }
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
            OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_WARNING_PERIOD), false,
            new DERUTF8String(warningPeriod)));
        if (sub.getContractNumber() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_CONTRACT_NUMBER_KEY),
                false, new DERUTF8String(sub.getContractNumber())));
        }

        return toReturn;
    }

    public List<X509ExtensionWrapper> entitlementExtensions(
        Entitlement entitlement) {
        String entitlementOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        return Collections.singletonList(new X509ExtensionWrapper(
            entitlementOid + "." +
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_QUANTITY_USED), false,
            new DERUTF8String(entitlement.getQuantity().toString())));

    }

    public Set<X509ExtensionWrapper> productExtensions(Product product) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();

        String productCertOid = OIDUtil.REDHAT_OID + "." +
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.PRODUCT_CERT_NAMESPACE_KEY);

        // XXX need to deal with non hash style IDs
        String productOid = productCertOid + "." + product.getId();
        // 10.10.10 is the product hash, arbitrary number atm
        // replace ith approriate hash for product, we can maybe get away with
        // faking this
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
            OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_NAME_KEY), false,
            new DERUTF8String(product.getName())));

        return toReturn;
    }

    public Set<X509ExtensionWrapper> contentExtensions(Product product) {
        Set<X509ExtensionWrapper> toReturn = new LinkedHashSet<X509ExtensionWrapper>();
        Set<ProductContent> productContent = product.getProductContent();

        // for (Content con : content) {
        for (ProductContent pc : productContent) {
            String contentOid = OIDUtil.REDHAT_OID +
                "." +
                OIDUtil.TOPLEVEL_NAMESPACES
                    .get(OIDUtil.CHANNEL_FAMILY_NAMESPACE_KEY) + "." +
                pc.getContent().getId().toString() + "." +
                OIDUtil.CF_REPO_TYPE.get(pc.getContent().getType());
            toReturn.add(new X509ExtensionWrapper(contentOid, false,
                new DERUTF8String(pc.getContent().getType())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_NAME_KEY), false,
                new DERUTF8String(pc.getContent().getName())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_LABEL_KEY), false,
                new DERUTF8String(pc.getContent().getLabel())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_VENDOR_ID_KEY),
                false, new DERUTF8String(pc.getContent().getVendor())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_DOWNLOAD_URL_KEY),
                false, new DERUTF8String(pc.getContent().getContentUrl())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_GPG_URL_KEY), false,
                new DERUTF8String(pc.getContent().getGpgUrl())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_FLEX_QUANTITY_KEY),
                false, new DERUTF8String(pc.getFlexEntitlement().toString())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_PHYS_QUANTITY_KEY),
                false,
                new DERUTF8String(pc.getPhysicalEntitlement().toString())));

            toReturn.add(new X509ExtensionWrapper(contentOid + "." +
                OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_ENABLED), false,
                new DERUTF8String((pc.getEnabled()) ? "1" : "0")));

        }
        return toReturn;
    }
}
