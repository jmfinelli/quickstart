package org.jboss.narayana.quickstarts;

import static org.junit.Assert.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import jakarta.transaction.TransactionManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.extension.byteman.api.BMRule;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.narayana.quickstarts.ejb.CustomerManagerEJB;
import org.jboss.narayana.quickstarts.ejb.CustomerManagerEJBImpl;
import org.jboss.narayana.quickstarts.jpa.Customer;
import org.jboss.narayana.quickstarts.jsf.CustomerManager;
import org.jboss.narayana.quickstarts.jsf.CustomerManagerManagedBean;
import org.jboss.narayana.quickstarts.txoj.CustomerCreationCounter;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.extras.creaper.core.online.FailuresAllowedBlock;
import org.wildfly.extras.creaper.core.online.OnlineManagementClient;
import org.wildfly.extras.creaper.core.online.OnlineOptions;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import static org.junit.Assert.fail;

@RunWith(Arquillian.class)
@ServerSetup(value = TestManagedBeanCustomerManager.ServerSetup.class)
public class TestManagedBeanCustomerManager {

	public static class ServerSetup implements ServerSetupTask {
		@Override
		public void setup(ManagementClient managementClient, String containerId) throws Exception {
			OnlineManagementClient creaper = org.wildfly.extras.creaper.core.ManagementClient.online(
					OnlineOptions.standalone().wrap(managementClient.getControllerClient()));

			try(FailuresAllowedBlock allowedBlock = creaper.allowFailures()) {
				creaper.execute("/subsystem=logging/periodic-rotating-file-handler=FILE:write-attribute(name=level,value=TRACE)");
				creaper.execute("/subsystem=logging/console-handler=CONSOLE:write-attribute(name=level,value=TRACE)");
				creaper.execute("/subsystem=logging/logger=com.arjuna:write-attribute(name=level,value=TRACE)");
			}
			new Administration(creaper).reload();
		}

		@Override
		public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
			OnlineManagementClient creaper = org.wildfly.extras.creaper.core.ManagementClient.online(
					OnlineOptions.standalone().wrap(managementClient.getControllerClient()));

			try(FailuresAllowedBlock allowedBlock = creaper.allowFailures()) {
				creaper.execute("/subsystem=logging/periodic-rotating-file-handler=FILE:write-attribute(name=level,value=WARN)");
				creaper.execute("/subsystem=logging/console-handler=CONSOLE:write-attribute(name=level,value=WARN)");
				creaper.execute("/subsystem=logging/logger=com.arjuna:write-attribute(name=level,value=WARN)");
			}
		}
	}

	@Inject
	private CustomerManagerManagedBean managedBeanCustomerManager;

	@Inject
	TransactionManager transactionManager;

	@Deployment
	public static WebArchive createDeployment() {
		WebArchive archive = ShrinkWrap
				.create(WebArchive.class, "test.war")
				.addClasses(CustomerManagerEJB.class,
						CustomerManagerEJBImpl.class, Customer.class)
				.addClasses(CustomerCreationCounter.class)
				.addClasses(CustomerManager.class,
						CustomerManagerManagedBean.class)
				.addAsResource("META-INF/persistence.xml",
						"META-INF/persistence.xml")
				.addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
		archive.delete(ArchivePaths.create("META-INF/MANIFEST.MF"));

		final String ManifestMF = "Manifest-Version: 1.0\n"
				+ "Dependencies: org.jboss.jts\n";
		archive.setManifest(new StringAsset(ManifestMF));

		return archive;
	}

	@Test
	@BMRule(
			name = "Delays the commitment of the transaction by 5 seconds",
			targetClass = "com.arjuna.ats.arjuna.coordinator.BasicAction", targetMethod = "prepare(boolean)",
			targetLocation = "EXIT",
			action = "debug(\"**** Byteman script checkListCustomers **** -> Delay of 5 seconds!\"), delay(10000)")
	public void checkListCustomers() throws Exception {

		transactionManager.setTransactionTimeout(1);
		// Create a customer
		managedBeanCustomerManager.addCustomer("Test"
				+ System.currentTimeMillis());
		List<Customer> firstList = managedBeanCustomerManager.getCustomers();
		// Create a different customer
		managedBeanCustomerManager.addCustomer("Test"
				+ System.currentTimeMillis());
		List<Customer> secondList = managedBeanCustomerManager.getCustomers();

		// Check that the list size increased
		assertTrue(firstList.size() < secondList.size());
	}
	@Test
	@BMRule(
			name = "Delays the commitment of the transaction by 5 seconds",
			targetClass = "com.arjuna.ats.arjuna.coordinator.BasicAction", targetMethod = "prepare(boolean)",
			targetLocation = "EXIT",
			action = "debug(\"**** Byteman script checkCustomerCount **** -> Delay of 5 seconds!\"), delay(5000)")
	public void checkCustomerCount() throws Exception {
		int response = -1;
		transactionManager.setTransactionTimeout(1);
		int size = managedBeanCustomerManager.getCustomerCount();

		// Create a new customer
		long time = System.currentTimeMillis();
		managedBeanCustomerManager.addCustomer("Test"
				+ time);

		// Get the initial number of customers
		response = managedBeanCustomerManager.getCustomerCount();
		assertTrue("" + response, response == size + 1);
		size = response;

		// Create a new customer
		long time2 = System.currentTimeMillis();
		if (time2 == time) {
			Thread.currentThread().sleep(1000);
			time2 = System.currentTimeMillis();
			if (time2 == time) {
				fail("time was the same");
			}
		}
		
		managedBeanCustomerManager.addCustomer("Test"
				+ time2);

		// Check that one extra customer was created
		response = managedBeanCustomerManager.getCustomerCount();
		assertTrue("" + response, response == size + 1);
		size = response;
	}
	@Test
	@BMRule(
			name = "Delays the commitment of the transaction by 5 seconds",
			targetClass = "com.arjuna.ats.arjuna.coordinator.BasicAction", targetMethod = "prepare(boolean)",
			targetLocation = "EXIT",
			action = "debug(\"**** Byteman script testCustomerCountInPresenceOfRollback **** -> Delay of 5 seconds!\"), delay(5000)")
	public void testCustomerCountInPresenceOfRollback() throws Exception {
		int response = -1;
		transactionManager.setTransactionTimeout(1);
		int size = managedBeanCustomerManager.getCustomerCount();

		String firstCustomerName = "Test" + System.currentTimeMillis();
		// Create a new customer
		managedBeanCustomerManager.addCustomer(firstCustomerName);

		// Get the initial number of customers
		response = managedBeanCustomerManager.getCustomerCount();
		assertTrue("" + response, response == size + 1);
		size = response;

		// Create a new customer
		managedBeanCustomerManager.addCustomer(firstCustomerName);

		// Check that no extra customers were created
		response = managedBeanCustomerManager.getCustomerCount();
		assertTrue("" + response, response == size);
		size = response;

		// Create a new customer
		managedBeanCustomerManager.addCustomer("Test"
				+ System.currentTimeMillis());

		// Check that one extra customer was created
		response = managedBeanCustomerManager.getCustomerCount();
		assertTrue("" + response, response == size + 1);
		size = response;
	}
}