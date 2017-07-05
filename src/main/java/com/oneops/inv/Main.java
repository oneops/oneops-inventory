package com.oneops.inv;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class Main {

    private static final String ENV_OO_API_TOKEN = "OO_API_TOKEN";
    private static final String ENV_OO_ORG = "OO_ORG";
    private static final String ENV_OO_ASSEMBLY = "OO_ASSEMBLY";
    private static final String ENV_OO_ENV = "OO_ENV";
    private static final String ENV_OO_ENDPOINT = "OO_ENDPOINT";
    private static final String ENV_OO_HOST_METHOD = "OO_HOST_METHOD";

    private static final String OO_ENDPOINT_DEFAULT = "https://prod.oneops.com/";
    private static final String OO_HOST_METHOD = "public_ip";


    public static void main( String[] args )
    {

        // Parse the commandline for Ansible arguments
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("list")
                .withDescription("List all inventory")
                .create());
        options.addOption(OptionBuilder.withLongOpt("host")
                .withDescription("List one host")
                .hasArg()
                .create());

        // Read Environment Variables for OO Coordinates
        String apiToken = System.getenv(Main.ENV_OO_API_TOKEN);
        String org = System.getenv(Main.ENV_OO_ORG);
        String assembly = System.getenv(Main.ENV_OO_ASSEMBLY);
        String env = System.getenv(Main.ENV_OO_ENV);
        String endpoint = System.getenv(Main.ENV_OO_ENDPOINT);
        if (StringUtils.isEmpty(endpoint)) {
            endpoint = Main.OO_ENDPOINT_DEFAULT;
        }
        String hostMethod = System.getenv(Main.ENV_OO_HOST_METHOD);
        if (StringUtils.isEmpty(hostMethod)) {
            hostMethod = Main.OO_HOST_METHOD;
        }

        validateEnvironment(apiToken, org, assembly, env, hostMethod);


        CommandLineParser parser = new DefaultParser();
        boolean isList = false;
        String host = "";


        try {
            CommandLine cmd = parser.parse( options, args);

            // Initialize the Inventory object with the environment vars for OO
            Inventory inventory = new Inventory(org, assembly, env, apiToken, endpoint, hostMethod);
            inventory.initialize();
            JSONObject dynamicInventory = null;

            isList = cmd.hasOption("list");
            if( cmd.hasOption("host")) {
                host = cmd.getOptionValue("host");
            }
            if( isList ) {
                dynamicInventory = inventory.generateList();
            } else if( !StringUtils.isEmpty(host) ) {
                dynamicInventory = inventory.generateHost(host);
            } else {
                // If no arguments were supplied, print the usage message
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "inventory", options );
            }

            // Print out the Dynamic Inventory if any was returned...
            if( dynamicInventory != null ) {
                System.out.print( dynamicInventory.toString( 2 ) );
            }
        } catch (ParseException e) {
            System.err.println( "Error parsing command line options" );
            e.printStackTrace();
            System.exit( 1 );
        } catch (InventoryException ie) {
            System.err.println( "Error generating inventory" );
            ie.printStackTrace();
            System.exit( 1 );
        }

        System.exit(0);

    }

    /**
     * Validate that the environment variables to connect to a OneOps are present.  All four are required.
     */
    private static void validateEnvironment(String apiToken, String org, String assembly, String env, String hostMethod) {
        if (StringUtils.isEmpty(apiToken)) {
            System.err.printf( "Environment variable %s must be defined", ENV_OO_API_TOKEN);
            System.exit(1);
        }

        if (StringUtils.isEmpty(org)) {
            System.err.printf( "Environment variable %s must be defined", ENV_OO_ORG);
            System.exit(1);
        }

        if (StringUtils.isEmpty(assembly)) {
            System.err.printf( "Environment variable %s must be defined", ENV_OO_ASSEMBLY);
            System.exit(1);
        }

        if (StringUtils.isEmpty(env)) {
            System.err.printf( "Environment variable %s must be defined", ENV_OO_ENV);
            System.exit(1);
        }

        if( !( hostMethod.equals("public_ip") || hostMethod.equals("private_ip") || hostMethod.equals("hostname") ) ) {
            System.err.printf( "Environment variable %s must be set to one of: public_ip, private_ip, or hostname", ENV_OO_HOST_METHOD );
        }

    }
}
