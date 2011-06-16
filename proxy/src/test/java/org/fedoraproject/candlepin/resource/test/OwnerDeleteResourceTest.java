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
package org.fedoraproject.candlepin.resource.test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.fedoraproject.candlepin.model.ConsumerTypeCurator;

import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.model.ActivationKeyCurator;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ExporterMetadataCurator;
import org.fedoraproject.candlepin.model.ImportRecordCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.OwnerPermissionCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.StatisticCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.resource.OwnerResource;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class OwnerDeleteResourceTest {

    private OwnerResource ownerResource;

    @Mock private OwnerCurator ownerCurator;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private PoolManager poolManager;
    @Mock private PoolCurator poolCurator;
    @Mock private SubscriptionCurator subscriptionCurator;
    @Mock private StatisticCurator statisticCurator;
    @Mock private ActivationKeyCurator subscriptionTokenCurator;
    @Mock private ExporterMetadataCurator exportCurator;
    @Mock private ImportRecordCurator importRecordCurator;
    @Mock private UserServiceAdapter userService;
    @Mock private EventFactory eventFactory;
    @Mock private EventSink eventSink;
    @Mock private SubscriptionServiceAdapter subAdapter;
    @Mock private OwnerPermissionCurator permCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;

    @Before
    public void init() {
        this.ownerResource = new OwnerResource(ownerCurator, poolCurator,
                subscriptionCurator, subscriptionTokenCurator, consumerCurator,
                statisticCurator, null, userService, eventSink, eventFactory, null,
                null, null, poolManager, exportCurator, null, importRecordCurator, subAdapter, 
                permCurator, consumerTypeCurator);
    }

    @Test
    public void skipUserDeletion() {
        Owner rackspace = new Owner("rackspace");

        when(ownerCurator.lookupByKey("rackspace")).thenReturn(rackspace);
        when(userService.isReadyOnly()).thenReturn(true);
        when(userService.listByOwner(rackspace)).thenThrow(
                new UnsupportedOperationException("This should never be called"));

        this.ownerResource.deleteOwner("rackspace", true);

        // Just asking for the users will throw an exception anyway, but this
        // just to be super duper sure
        verify(userService, never()).deleteUser(any(User.class));
    }

    @Test
    public void usersAreDeleted() {
        Owner compucorp = new Owner("compucorp");
        List<User> users = new ArrayList<User>();
        User billy = new User("billy", "password");
        User sally = new User("sally", "password");
//        billy.addMembershipTo(compucorp);
//        sally.addMembershipTo(compucorp);
        users.add(billy);
        users.add(sally);

        when(ownerCurator.lookupByKey("compucorp")).thenReturn(compucorp);
        when(userService.isReadyOnly()).thenReturn(false);
        when(userService.listByOwner(compucorp)).thenReturn(users);

        this.ownerResource.deleteOwner("compucorp", true);

        // Make sure they were deleted in the service
        verify(userService).deleteUser(users.get(0));
        verify(userService).deleteUser(users.get(1));
    }
}
