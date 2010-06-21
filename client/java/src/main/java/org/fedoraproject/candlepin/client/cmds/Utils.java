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
package org.fedoraproject.candlepin.client.cmds;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.SystemUtils;
import org.fedoraproject.candlepin.client.ClientException;
import org.fedoraproject.candlepin.client.Constants;

/**
 * The Class Utils.
 */
public final class Utils {

    /**
     *
     */
    private static final String DEF_PROPERTIES_PATH =
        "org/fedoraproject/candlepin/client/defaultValues.properties";

    private Utils() {
        //prevent instantiation
    }

    public static <E> List<E> newList() {
        return new ArrayList<E>();
    }

    public static <K, V> Map<K, V> newMap() {
        return new HashMap<K, V>();
    }

    public static Properties getDefaultProperties() {
        try {
            Properties properties = new Properties();
            properties.load(
                ClassLoader.getSystemResourceAsStream(DEF_PROPERTIES_PATH));
            replaceSystemPropertyValues(properties, Constants.CP_HOME_DIR);
            return properties;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void replaceSystemPropertyValues(Properties properties, String key) {
        properties.setProperty(key,
            String.format(properties.getProperty(key), SystemUtils.USER_HOME));
    }

    public static <T> Set<T> newSet() {
        return new HashSet<T>();
    }

    public static int[] toInt(String[] strs) {
        int[] ints = new int[ArrayUtils.nullToEmpty(strs).length];
        for (int i = 0; i < ints.length; i++) {
            try {
                ints[i] = Integer.parseInt(strs[i].trim());
            }
            catch (NumberFormatException e) {
                throw new ClientException("{" + strs[i] + "} is not a number");
            }
        }
        return ints;
    }

    public static int getSafeInt(int[] quantity, int index, int i) {
        if (quantity.length - 1 < index) {
            return i;
        }
        return quantity[index];
    }

    /**
     * To string.
     * 
     * @param <K> the key type
     * @param <V> the value type
     * @param msg the msg
     * @return the string
     */
    public static <K, V> String toStr(Map<K, V> msg) {
        StringBuilder builder = new StringBuilder("\n---- Begin Map-----\n");
        for (Entry<K, V> entry : msg.entrySet()) {
            builder.append(entry.getKey().toString()).append("=").append(
                entry.getValue().toString()).append("\n");
        }
        return builder.append("---- End Map -----\n").toString();
    }

    /**
     * The Class DummyTrustManager.
     */
    public static final X509TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        }
    };

    public static final TrustManager [] DUMMY_TRUST_MGRS = new TrustManager[]{
        DUMMY_TRUST_MANAGER
    };
}
