package com.oneops.inv;

import javax.annotation.Nullable;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class Main {

    private static final String ENV_OO_API_TOKEN = "OO_API_TOKEN";
    private static final String ENV_OO_ORG = "OO_ORG";
    private static final String ENV_OO_ASSEMBLY = "OO_ASSEMBLY";
    private static final String ENV_OO_ENV = "OO_ENV";
    private static final String ENV_OO_ENDPOINT = "OO_ENDPOINT";
    private static final String ENV_OO_HOST_METHOD = "OO_HOST_METHOD";

    private static final String OO_HOST_METHOD = "public_ip";

    private String apiToken;

    private String org;

    private String assembly;

    @Nullable
    private String env;

    private String endpoint;

    private String hostMethod;

    /**
     * Validate basic environment configuration.
     */
    public Main() {
        // Read Environment Variables for OO Coordinates
        apiToken = System.getenv(Main.ENV_OO_API_TOKEN);
        if (StringUtils.isEmpty(apiToken)) {
            die("Missing required environment variable: " + ENV_OO_API_TOKEN);
        }

        org = System.getenv(Main.ENV_OO_ORG);
        if (StringUtils.isEmpty(org)) {
            die("Missing required environment variable: " + ENV_OO_ORG);
        }

        assembly = System.getenv(Main.ENV_OO_ASSEMBLY);
        if (StringUtils.isEmpty(assembly)) {
            die("Missing required environment variable: " + ENV_OO_ASSEMBLY);
        }

        env = System.getenv(Main.ENV_OO_ENV);

        endpoint = System.getenv(Main.ENV_OO_ENDPOINT);
        if (StringUtils.isEmpty(endpoint)) {
            die("Missing required environment variable: " + ENV_OO_ENDPOINT);
        }
        if (!StringUtils.endsWith(endpoint, "/")) {
            die("Environment variable must end with a forward-slash: " + ENV_OO_ENDPOINT);
        }

        hostMethod = System.getenv(Main.ENV_OO_HOST_METHOD);
        if (StringUtils.isEmpty(hostMethod)) {
            hostMethod = Main.OO_HOST_METHOD;
        }
        if (!(hostMethod.equals("public_ip") || hostMethod.equals("private_ip") || hostMethod.equals("hostname"))) {
            die("Environment variable " + ENV_OO_HOST_METHOD + " must be set to one of: public_ip, private_ip, or hostname");
        }
    }

    /**
     * Process command-line and display inventory as configured.
     */
    public void run(final String[] args) {
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
                displayInventory(null);
            }
            else if (cmd.hasOption("host")) {
                displayInventory(cmd.getOptionValue("host"));
            }
            else {
                // If no arguments were supplied, print the usage message
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("inventory", options);
            }
        }
        catch (ParseException e) {
            die("Error parsing command-line options", e);
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
     * Display an error message, optionally display a stack-trace and exit with {@code 1}.
     */
    private static void die(final String message, @Nullable final Throwable cause) {
        System.err.println(message);
        if (cause != null) {
            cause.printStackTrace();
        }
        System.exit(1);
    }

    /**
     * @see #die(String, Throwable)
     */
    private static void die(final String message) {
        die(message, null);
    }

    /**
     * Bootstrap.
     */
    public static void main(final String[] args) {
        new Main().run(args);
        System.exit(0);
    }
}
