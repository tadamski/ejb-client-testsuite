/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.ejbclient.testsuite.integration.basic.tx;

import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.wildfly.ejbclient.testsuite.integration.basic.utils.jndi.InitialContextDirectory;
import org.wildfly.ejbclient.testsuite.integration.basic.utils.EJBClientContextType;
import org.wildfly.ejbclient.testsuite.integration.basic.utils.TestEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.naming.client.WildFlyInitialContextFactory;

/**
 * @author Jan Martiska
 */
@RunWith(Arquillian.class)
@RunAsClient
public class TransactionTestCase_usingNewAPIFrom_EJBCLIENT4 {

    public static final String DEPLOYMENT_NAME = "transaction-test";

    @Deployment
    public static WebArchive deployment() {
        final WebArchive webArchive = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war");
        webArchive.addPackage(TransactionalBean.class.getPackage());
        webArchive.addAsWebInfResource(ClassLoader.getSystemResource("persistence.xml"),
                "classes/META-INF/persistence.xml");
        return webArchive;
    }

    @BeforeClass
    public static void onlyForGlobalContexts() {
        Assume.assumeTrue(TestEnvironment.getContextType().equals(EJBClientContextType.GLOBAL));
    }

    @Before
    public void cleanup() throws NamingException {
        try(final InitialContextDirectory ctx = new InitialContextDirectory.Supplier().get()) {
            final TransactionalBeanRemote bean = ctx
                    .lookupStateless(DEPLOYMENT_NAME, TransactionalBean.class, TransactionalBeanRemote.class);
            bean.clean();
        }
    }

    @Test
    public void testCommit()
            throws NamingException, SystemException, NotSupportedException, HeuristicRollbackException,
            HeuristicMixedException, RollbackException {

        InitialContext ctxWithAffinity = null;
        try {
            ctxWithAffinity = getContextWithAffinity();
            final UserTransaction tx = (UserTransaction)ctxWithAffinity.lookup("txn:UserTransaction");
            try(final InitialContextDirectory ctx = new InitialContextDirectory.Supplier().get()) {
                final TransactionalBeanRemote bean = ctx
                        .lookupStateless(DEPLOYMENT_NAME, TransactionalBean.class, TransactionalBeanRemote.class);
                tx.begin();
                bean.createPerson();
                tx.commit();
                Assert.assertEquals("There should be a person in the DB, the transaction was committed", 1, bean.getPersonList().size());
            }
        } finally {
            if (ctxWithAffinity != null) {
                ctxWithAffinity.close();
            }
        }
    }

    @Test
    public void testRollback()
            throws NamingException, SystemException, NotSupportedException, HeuristicRollbackException,
            HeuristicMixedException, RollbackException {

        InitialContext ctxWithAffinity = null;
        try {
            ctxWithAffinity = getContextWithAffinity();
            final UserTransaction tx = (UserTransaction)ctxWithAffinity.lookup("txn:UserTransaction");
            try(final InitialContextDirectory ctx = new InitialContextDirectory.Supplier().get()) {
                final TransactionalBeanRemote bean = ctx
                        .lookupStateless(DEPLOYMENT_NAME, TransactionalBean.class, TransactionalBeanRemote.class);
                tx.begin();
                bean.createPerson();
                tx.rollback();
                Assert.assertTrue("There should be nothing in the DB, the transaction was rolled back", bean.getPersonList().isEmpty());
            }
        } finally {
            if (ctxWithAffinity != null) {
                ctxWithAffinity.close();
            }
        }
    }

    public InitialContext getContextWithAffinity() throws NamingException {
        Properties props = new Properties();
        props.put(Context.PROVIDER_URL, TestEnvironment.getConnectorType().getConnectionScheme()
                + "://127.0.0.1:"
                + TestEnvironment.getServerPort());
        props.put(Context.INITIAL_CONTEXT_FACTORY, WildFlyInitialContextFactory.class.getName());

        return new InitialContext(props);
    }
}
