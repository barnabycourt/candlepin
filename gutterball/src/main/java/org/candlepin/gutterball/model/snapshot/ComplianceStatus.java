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

package org.candlepin.gutterball.model.snapshot;

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.gutterball.jackson.MapToKeysConverter;
import org.candlepin.gutterball.jackson.CompliantProductReferenceConverter;
import org.candlepin.gutterball.jackson.NonCompliantProductReferenceConverter;
import org.candlepin.gutterball.jackson.PartiallyCompliantProductReferenceConverter;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Model object that represents a consumer's status at a given point in time.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_compliance_status_snap")
@JsonFilter("GBComplianceStatusFilter")
@HateoasInclude
public class ComplianceStatus {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_snap_id", nullable = false)
    @NotNull
    private Compliance complianceSnapshot;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date date;

    @XmlElement
    @Column(nullable = true, unique = false, name = "compliant_until")
    private Date compliantUntil;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String status;

    @OneToMany(mappedBy = "complianceStatus", targetEntity = ComplianceReason.class, fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ComplianceReason> reasons;

    @OneToMany(mappedBy = "complianceStatus",
        targetEntity = CompliantProductReference.class, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonDeserialize(converter = CompliantProductReferenceConverter.class)
    private Set<CompliantProductReference> compliantProducts;

    @OneToMany(mappedBy = "complianceStatus",
        targetEntity = PartiallyCompliantProductReference.class, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonDeserialize(converter = PartiallyCompliantProductReferenceConverter.class)
    private Set<PartiallyCompliantProductReference> partiallyCompliantProducts;

    @OneToMany(mappedBy = "complianceStatus",
        targetEntity = NonCompliantProductReference.class, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JsonDeserialize(converter = NonCompliantProductReferenceConverter.class)
    private Set<NonCompliantProductReference> nonCompliantProducts;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    @CollectionTable(name = "gb_partialstack_snap", joinColumns = @JoinColumn(name = "comp_status_id"))
    @Column(name = "stacking_id")
    @JsonDeserialize(converter = MapToKeysConverter.class)
    private Set<String> partialStacks;

    @Column(name = "management_enabled")
    private Boolean managementEnabled;

    public ComplianceStatus() {
        // Required by hibernate.
        reasons = new HashSet<ComplianceReason>();
        this.compliantProducts = new HashSet<CompliantProductReference>();
        this.partiallyCompliantProducts = new HashSet<PartiallyCompliantProductReference>();
        this.nonCompliantProducts = new HashSet<NonCompliantProductReference>();
        this.partialStacks = new HashSet<String>();
        this.managementEnabled = Boolean.FALSE;
    }

    public ComplianceStatus(Date date, String status) {
        this();
        this.date = date;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public Compliance getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(Compliance complianceSnapshot) {
        this.complianceSnapshot = complianceSnapshot;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Set<ComplianceReason> getReasons() {
        return reasons;
    }

    public void setReasons(Set<ComplianceReason> reasons) {
        if (reasons == null) {
            reasons = new HashSet<ComplianceReason>();
        }
        this.reasons = reasons;

        for (ComplianceReason r : this.reasons) {
            r.setComplianceStatus(this);
        }
    }

    public Date getCompliantUntil() {
        return compliantUntil;
    }

    public void setCompliantUntil(Date compliantUntil) {
        this.compliantUntil = compliantUntil;
    }

    public Set<NonCompliantProductReference> getNonCompliantProducts() {
        return nonCompliantProducts;
    }

    public void setNonCompliantProducts(Set<NonCompliantProductReference> nonCompliantProducts) {
        this.nonCompliantProducts = nonCompliantProducts;
    }

    public Set<CompliantProductReference> getCompliantProducts() {
        return compliantProducts;
    }

    public void setCompliantProducts(Set<CompliantProductReference> compliantProducts) {
        this.compliantProducts = compliantProducts;
    }

    public Set<PartiallyCompliantProductReference> getPartiallyCompliantProducts() {
        return partiallyCompliantProducts;
    }

    public void setPartiallyCompliantProducts(
        Set<PartiallyCompliantProductReference> partiallyCompliantProducts) {

        this.partiallyCompliantProducts = partiallyCompliantProducts;
    }

    public Set<String> getPartialStacks() {
        return partialStacks;
    }

    public void setPartialStacks(Set<String> partialStacks) {
        this.partialStacks = partialStacks;
    }

    public Boolean getManagementEnabled() {
        return managementEnabled;
    }

    public void setManagementEnabled(Boolean managementEnabled) {
        this.managementEnabled = managementEnabled;
    }


    @PrePersist
    protected void onCreate() {
        this.onUpdate();
    }

    @PreUpdate
    protected void onUpdate() {
        // Update all of our contained objects to ensure they point to this as their parent
        for (AbstractProductReference pr : this.compliantProducts) {
            pr.setComplianceStatus(this);
        }

        for (AbstractProductReference pr : this.partiallyCompliantProducts) {
            pr.setComplianceStatus(this);
        }

        for (AbstractProductReference pr : this.nonCompliantProducts) {
            pr.setComplianceStatus(this);
        }
    }


}
