package com.oneops.inv;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import static com.google.common.base.Preconditions.checkState;

public class Main
{
    @VisibleForTesting
    static final String ENV_OO_API_TOKEN = "OO_API_TOKEN";

    @VisibleForTesting
    static final String ENV_OO_ORG = "OO_ORG";

    @VisibleForTesting
    static final String ENV_OO_ASSEMBLY = "OO_ASSEMBLY";

    @VisibleForTesting
    static final String ENV_OO_ENV = "OO_ENV";

    @VisibleForTesting
    static final String ENV_OO_ENDPOINT = "OO_ENDPOINT";

    @VisibleForTesting
    static final String DEFAULT_HOST_METHOD = "public_ip";

    @VisibleForTesting
    static final String ENV_OO_HOST_METHOD = DEFAULT_HOST_METHOD;

    private String apiToken;

    private String org;

    private String assembly;

    @Nullable
    private String env;

    private String endpoint;

    private String hostMethod;

    @Nullable
    private String host;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(final String apiToken) {
        this.apiToken = apiToken;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(final String org) {
        this.org = org;
    }

    public String getAssembly() {
        return assembly;
    }

    public void setAssembly(final String assembly) {
        this.assembly = assembly;
    }

    @Nullable
    public String getEnv() {
        return env;
    }

    public void setEnv(@Nullable final String env) {
        this.env = env;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final String endpoint) {
        this.endpoint = endpoint;
    }

    public String getHostMethod() {
        return hostMethod;
    }

    public void setHostMethod(final String hostMethod) {
        this.hostMethod = hostMethod;
    }

    @Nullable
    public String getHost() {
        return host;
    }

    public void setHost(@Nullable final String host) {
        this.host = host;
    }

    /**
     * Return environment-variable value for given name.
     */
    @VisibleForTesting
    @Nullable
    protected String readEnvironment(final String name) {
        return System.getenv(name);
    }

    /**
     * Configure settings from environment variables.
     */
    public void configureFromEnvironment() {
        boolean valid = true;

        // Read Environment Variables for OO Coordinates
        apiToken = readEnvironment(Main.ENV_OO_API_TOKEN);
        if (StringUtils.isEmpty(apiToken)) {
            System.err.println("Missing required environment variable: " + ENV_OO_API_TOKEN);
            valid = false;
        }

        org = readEnvironment(Main.ENV_OO_ORG);
        if (StringUtils.isEmpty(org)) {
            System.err.println("Missing required environment variable: " + ENV_OO_ORG);
            valid = false;
        }

        assembly = readEnvironment(Main.ENV_OO_ASSEMBLY);
        if (StringUtils.isEmpty(assembly)) {
            System.err.println("Missing required environment variable: " + ENV_OO_ASSEMBLY);
            valid = false;
        }

        env = readEnvironment(Main.ENV_OO_ENV);

        endpoint = readEnvironment(Main.ENV_OO_ENDPOINT);
        if (StringUtils.isEmpty(endpoint)) {
            System.err.println("Missing required environment variable: " + ENV_OO_ENDPOINT);
            valid = false;
        }
        else if (!StringUtils.endsWith(endpoint, "/")) {
            System.err.println("Environment variable must end with a forward-slash: " + ENV_OO_ENDPOINT);
            valid = false;
        }

        hostMethod = readEnvironment(Main.ENV_OO_HOST_METHOD);
        if (StringUtils.isEmpty(hostMethod)) {
            hostMethod = Main.DEFAULT_HOST_METHOD;
        }
        if (!(hostMethod.equals("public_ip") || hostMethod.equals("private_ip") || hostMethod.equals("hostname"))) {
            System.err.println("Environment variable " + ENV_OO_HOST_METHOD + " must be set to one of: public_ip, private_ip, or hostname");
            valid = false;
        }

        if (!valid) {
            throw new ExitNotification(1);
        }
    }

    /**
     * Configure settings from command-line arguments.
     */
    public void configureFromCommandLine(final String[] args) {
        // Parse the commandline for Ansible arguments
        Options options = new Options();
        options.addOption(Option.builder().longOpt("list")
            .desc("List all inventory")
            .build()
        );
        options.addOption(Option.builder().longOpt("host")
            .desc("List one host")
            .hasArg()
            .build()
        );

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("list")) {
                host = null;
            }
            else if (cmd.hasOption("host")) {
                host = cmd.getOptionValue("host");
            }
            else {
                // If no arguments were supplied, print the usage message
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("inventory", options);
                throw new ExitNotification(1);
            }
        }
        catch (ParseException e) {
            die("Error parsing command-line options", e);
        }
    }

    public void run() {
        checkState(apiToken != null, "Missing api-token");
        checkState(org != null, "Missing org");
        checkState(assembly != null, "Missing assembly");
        checkState(endpoint != null, "Missing endpoint");
        checkState(hostMethod != null, "Missing host-method");

        try {
            displayInventory(host);
        }
        catch (InventoryException e) {
            die("Error generating inventory", e);
        }
    }

    /**
     * Generate inventory for host, or if {@code null} generate a list.
     */
    private JSONObject generateInventory(@Nullable final String host) throws InventoryException {
        // Initialize the Inventory object with the environment vars for OO
        Inventory inventory = new Inventory(org, assembly, env, apiToken, endpoint, hostMethod);
        inventory.initialize();
        if (!StringUtils.isEmpty(host)) {
            return inventory.generateHost(host);
        }
        else {
            return inventory.generateList();
        }
    }

    /**
     * Display the inventory if anything is returned.
     */
    private void displayInventory(@Nullable final String host) throws InventoryException {
        JSONObject inventory = generateInventory(host);
        if (inventory != null) {
            System.out.println(inventory.toString(2));
        }
    }

    /**
     * Display an error message, optionally display a stack-trace and throw {@link ExitNotification} with {@code 1}.
     */
    private static void die(final String message, @Nullable final Throwable cause) {
        System.err.println(message);
        if (cause != null) {
            cause.printStackTrace();
        }
        throw new ExitNotification(1);
    }

    /**
     * Thrown to indicate the system should exit.
     */
    @VisibleForTesting
    static class ExitNotification extends Error
    {
        public final int code;

        public ExitNotification(final int code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" +
                "code=" + code +
                '}';
        }
    }

    /**
     * Bootstrap.
     */
    public static void main(final String[] args) {
        try {
            Main main = new Main();
            main.configureFromEnvironment();
            main.configureFromCommandLine(args);
            main.run();
            System.exit(0);
        }
        catch (ExitNotification n) {
            System.exit(n.code);
        }
        catch (Exception e) {
            System.err.println("Unexpected failure");
            e.printStackTrace();
            System.exit(2);
        }
    }
}
