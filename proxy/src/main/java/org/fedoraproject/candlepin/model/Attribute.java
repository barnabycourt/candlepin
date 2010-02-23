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

package org.fedoraproject.candlepin.model;

import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

/**
 * Attributes can be thought of as a hint on some restriction on the usage of an
 * entitlement. They will not actually contain the logic on how to enforce the
 * Attribute, but basically just act as a constant the policy rules can look
 * for. Attributes may also be used to carry more complex JSON data specific to
 * a particular deployment of Candlepin.
 *
 * Attributes are used by both Products and Entitlement Pools.
 */
@Entity
@Table(name = "cp_attribute")
@SequenceGenerator(name = "seq_attribute", sequenceName = "seq_attribute",
        allocationSize = 1)
@Embeddable
public class Attribute  implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_attribute")
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
//    @Column(nullable = false)
    @Column
    private String value;

    private Boolean containsJson = Boolean.FALSE;

    /**
     * default ctor
     */
    public Attribute() {

    }

    /**
     * @param name attribute name
     * @param quantity quantity of the attribute.
     */
    public Attribute(String name, String quantity) {
        this.name = name;
        this.value = quantity;
    }

    public String getName() {
        return name;
    }
    
    /**
     * @return the id
     */
    public Long getId() {
        return this.id;
    }
    
    
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
    
    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Attribute)) {
            return false;
        }
        
        Attribute another = (Attribute) anObject;
        
        return 
            name.equals(another.getName()) &&
            value.equals(another.getValue());
    }
    
    @Override
    public int hashCode() {
        return name.hashCode() * 31 + value.hashCode();
    }

    public Boolean getContainsJson() {
        return containsJson;
    }

    public Boolean containsJson() {
        return getContainsJson();
    }

    public void setContainsJson(Boolean containsJson) {
        this.containsJson = containsJson;
    }

    /**
     * Convert a value containing json to a map.
     */
    public Map getValueMap() {
        if (!getContainsJson()) {
            throw new RuntimeException("Attribute value does not contain JSON.");
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map result = mapper.readValue(value, new
                TypeReference<Map>() {} );
            return result;
        }
        catch (Exception e) {
            throw new RuntimeException("Error parsing json: " + value, e);
        }

    }
}
