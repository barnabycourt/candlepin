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
package org.fedoraproject.candlepin.resource;

import java.io.InputStream;
import java.util.Properties;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Status;

import com.google.inject.Inject;

/**
 * Status Resource
 */
@Path("/status")
public class StatusResource {

    private static Logger log = Logger.getLogger(StatusResource.class);
    
    /**
     * The current version of candlepin
     */
    private String version = "Unknown";
    
    /**
     * The current git hash
     */
    private String hash = "Unknown";
    
    @Inject
    public StatusResource() {
        try {
            InputStream in = this.getClass().getClassLoader().
                getResourceAsStream("build.properties");
            Properties props = new Properties();
            props.load(in);
            version = props.getProperty("version");
            hash = props.getProperty("hash");
            in.close();
        } 
        catch (Exception e) {
            log.error("Can not load build properties", e);
        }
    }

    /**
     * status to see if a server is up and running
     * 
     * @return the running status
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    public Status status() {
        Status status = new Status(true, version, hash);
        return status;
    }
}
