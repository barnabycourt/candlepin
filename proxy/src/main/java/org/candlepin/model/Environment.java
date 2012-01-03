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
package org.candlepin.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Index;

/**
 * Represents an environment within an owner/organization. Environments are tracked
 * primarily so we can enable/disable/promote content in specific places.
 *
 * Not all deployments of Candlepin will make use of this table, it will at times
 * be completely empty.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_environment")
public class Environment extends AbstractHibernateObject implements Serializable,
    Owned {

    @ManyToOne
    @ForeignKey(name = "fk_env_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_env_owner_fk_idx")
    private Owner owner;

    @Id
    @Column(length = 32)

    private String id;


    public Environment() {
    }

    public Environment(String id, Owner owner) {
        this.id = id;
        this.owner = owner;
    }


    /**
     * Get the environment ID.
     *
     * Note that we do not generate environment IDs as we do for most other model objects.
     * Environments are expected to be externally defined and thus we only store their
     * ID.
     *
     * @return environment ID
     */
    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

}
