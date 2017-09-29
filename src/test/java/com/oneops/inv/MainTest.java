package com.oneops.inv;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.oneops.inv.Main.ExitNotification;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link Main}.
 */
public class MainTest
{
    private Main underTest;

    private Map<String,String> environment;

    private void assertExitCode(final ExitNotification notification, final int code) {
        assertThat(notification.code, is(code));
    }

    @Before
    public void setUp() throws Exception {
        environment = new HashMap<>();
        environment.put(Main.ENV_OO_API_TOKEN, "foo");
        environment.put(Main.ENV_OO_ORG, "bar");
        environment.put(Main.ENV_OO_ASSEMBLY, "baz");
        environment.put(Main.ENV_OO_ENV, "qux");
        environment.put(Main.ENV_OO_ENDPOINT, "http://oneops.example.com/");
        environment.put(Main.ENV_OO_HOST_METHOD, Main.DEFAULT_HOST_METHOD);

        underTest = new Main()
        {
            @Nullable
            @Override
            protected String readEnvironment(final String name) {
                return environment.get(name);
            }
        };
    }

    @Test
    public void noOptionsExitsWithCode_1() throws Exception {
        try {
            underTest.configureFromCommandLine(new String[0]);
            fail();
        }
        catch (ExitNotification n) {
            assertExitCode(n, 1);
        }
    }

    @Test
    public void hostOptionCapturesValue() throws Exception {
        underTest.configureFromCommandLine(new String[] { "--host", "foo"});
        assertThat(underTest.getHost(), is("foo"));
    }

    @Test
    public void listOptionProceeds() throws Exception {
        underTest.configureFromCommandLine(new String[] { "--list" });
        assertThat(underTest.getHost(), nullValue());
    }

    @Test
    public void missingEnvironment() throws Exception {
        try {
            environment.clear();
            underTest.configureFromEnvironment();
            fail();
        }
        catch (ExitNotification n) {
            assertExitCode(n, 1);
        }
    }

    @Test
    public void ensureRequiredEnvironmentVariables() throws Exception {
        underTest.configureFromEnvironment();
    }

    @Test
    public void ensureEndpointEndsWithSlash() throws Exception {
        try {
            environment.put(Main.ENV_OO_ENDPOINT, "http://oneops.example.com"); // does not end with "/"
            underTest.configureFromEnvironment();
        }
        catch (ExitNotification n) {
            assertExitCode(n, 1);
        }
    }

    @Test
    public void ensureHostMehodInvalid() throws Exception {
        try {
            environment.put(Main.ENV_OO_HOST_METHOD, "junk");
            underTest.configureFromEnvironment();
        }
        catch (ExitNotification n) {
            assertExitCode(n, 1);
        }
    }

    @Test
    public void ensureHostMethod_public_ip() throws Exception {
        environment.put(Main.ENV_OO_HOST_METHOD, "public_ip");
        underTest.configureFromEnvironment();
    }

    @Test
    public void ensureHostMethod_private_ip() throws Exception {
        environment.put(Main.ENV_OO_HOST_METHOD, "private_ip");
        underTest.configureFromEnvironment();
    }

    @Test
    public void ensureHostMethod_hostname() throws Exception {
        environment.put(Main.ENV_OO_HOST_METHOD, "hostname");
        underTest.configureFromEnvironment();
    }
}
