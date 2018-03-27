package com.oneops.inv;

import com.oneops.api.OOInstance;
import com.oneops.api.exception.OneOpsClientAPIException;
import com.oneops.api.resource.Cloud;
import com.oneops.api.resource.Design;
import com.oneops.api.resource.Operation;
import com.oneops.api.resource.Transition;
import com.oneops.api.resource.model.CiResource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;


/**
 * DISCLAIMER - THIS CODE IS THE WORST.   IT IS TEMPORARY AND EXPLORATORY.
 */
public class Inventory
{
    private String org;
    private String assembly;
    private String env;
    private String apiToken;
    private String endpoint;
    private String hostMethod;
    
    /* 
    A regular expression to match valid IPv4 addresses. This is used to exclude IP addresses from the list of hostnames.
    as oneops returns the IP as a hostname, causing IPs to be used even when OO_HOST_METHOD is set to "hostname"
    */
    private static final String IPV4_REGEX = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

    /**
     * A confusing group of collections.  Quick explanation - we grab all the compute
     * information from the OO API up front, but then we create some indexes (Maps) to facilitate
     * lookups by IP addresses or component names.
     *
     * It's confusing, but read the comments inline and you'll understand the purpose of each one.
     */

    // All Compute Type CiResources for an Assembly
    private List<CiResource> allHosts = new ArrayList<CiResource>();

    // All Cloud CiResources - Used to lookup Cloud names from compute references
    private Map<String,CiResource> allClouds = new HashMap<String,CiResource>();

    /**
     *     Map of CiResources by the host identifier (which is configurable via hostMethod)
     *
     *     Why do we need this?  OneOps doesn't provide a way to look up a compute by IP address directly.
     *     We generate this index when we iterate through computes.   This seems like a hack, and I'm sure
     *     we could make a separate call to the Search API to gather this data.
     */
    private Map<String, CiResource> hostsById = new HashMap<String, CiResource>();

    /**
     *     Mapping between compute operations CiResource and the associated compute transition CiResource
     *
     *     Confused?  You should be - many "operations" CiResources for a compute instance map to a "transition"
     *     CiResource for this compute. Because certain assemblies use multiple compute components within a platform
     *     we need to reference the compute oo_component_name.  This is mostly to enable the use case for teams that
     *     handle data that have multiple compute components (not a common use case, but an important one.)
     */
    private Map<CiResource,CiResource> computeComponent = new HashMap<CiResource,CiResource>();

    /**
     * A Map between the name of the compute component and a map of computes by "instance id" - see below
     *
     * What is an instance name? If a component instance is named "compute-1111223-2" the instance name is "1111223-2" this
     * identifier is used to identify the dependencies and relationships between related components.   This Map
     * stores a key "hostname" and the map it contains stores instance-ids.
     *
     * Why? When we generate hostvars we need to find an instance's sibling component for "os" and "hostname".
     *
     * Yes, this is confusing.  A better API that returns a group of related CiResources would help here.
     */
    private Map<String,Map<String,CiResource>> instanceMapsByComponentName = new HashMap<String,Map<String,CiResource>>();

    /**
     * Every cloud is associated with zero or more cloud variables.  We include these as hostvars
     */
    private Map<CiResource, List<CiResource>> cloudVarsMap = new HashMap<CiResource, List<CiResource>>();

    /**
     * Every environment is associated with zero or more global variables.  We include these both
     * as groupvars and as hostvars.
     */
    private Map<CiResource, List<CiResource>> globalVarsMap = new HashMap<CiResource, List<CiResource>>();

    /**
     * Every platform is associated with zero or more local variables.  We include these both
     * as groupvars and as hostvars.
     */
    private Map<CiResource, List<CiResource>> platformVarsMap = new HashMap<CiResource, List<CiResource>>();


    /**
     * These Maps are here to hold Ansible groups.  We populate these as we cycle through all hosts.
     */
    // List of Compute Typed CiResources grouped by Assembly
    private Map<CiResource, List<CiResource>> assemblyHosts = new HashMap<CiResource, List<CiResource>>();

    // List of Compute Typed CiResources grouped by Environment
    private Map<CiResource, List<CiResource>> envHosts = new HashMap<CiResource, List<CiResource>>();

    // List of Compute Typed CiResources grouped by Platform
    private Map<CiResource, List<CiResource>> platformHosts = new HashMap<CiResource, List<CiResource>>();

    // List of Compute Typed CiResources grouped by Platform and Compute Type
    private Map<PlatformCompute, List<CiResource>> platformComputeHosts = new HashMap<PlatformCompute, List<CiResource>>();

    /**
     * Allows us to quickly get a reference to the environment and platform for a particular host or platform.
     */
    private Map<CiResource,CiResource> envByHostMap = new HashMap<CiResource,CiResource>();
    private Map<CiResource,CiResource> envByPlatformMap = new HashMap<CiResource, CiResource>();
    private Map<CiResource,CiResource> platformByHostMap = new HashMap<CiResource,CiResource>();



    /**
     * Create an instance of Inventory
     *
     * @param org String identifier of OneOps organization
     * @param assembly String identifier of OneOps assembly
     * @param env String identifier of OneOps environment
     * @param apiToken A OneOps API token
     */
    public Inventory(String org, String assembly, String env, String apiToken, String endpoint, String hostMethod) {
        this.org = org;
        this.assembly = assembly;
        this.env = env;
        this.apiToken = apiToken;
        this.endpoint = endpoint;
        this.hostMethod = hostMethod;
    }

    /**
     * Initialize the Inventory object, connect to OneOps, and gather inventory data.  This
     * method will invoke OneOps APIs and iterate through all computes.
     */
    public void initialize() throws InventoryException {
        OOInstance instance = new OOInstance();

        instance.setAuthtoken(apiToken);
        instance.setOrgname(org);
        instance.setEndpoint(endpoint);
        instance.setAssembly(assembly);
        instance.setEnvironment(env);

        gatherOneOpsData(instance, assembly, env);
    }

    /**
     * Connect to OneOps, gather all components and hosts associated with an assembly, and
     * populate the various indexes we use to generate inventory JSON.
     *
     * @param instance A OneOps OOInstance
     * @param assembly The name of an assembly
     */
    private void gatherOneOpsData(OOInstance instance, String assembly, String env) throws InventoryException {
        // Get all the Platforms
        try {

            // Gather all known clouds for this instance.  We gather this because there are references
            // to clouds and we need to retrieve variables and cloud configuration.
            Cloud cloud = new Cloud(instance);
            List<CiResource> clouds = cloud.listClouds();
            for( CiResource cld : clouds ) {
                allClouds.put( cld.getCiName(), cld );

                // TODO: THERES NO WAY TO GET CLOUD VARIABLES?  WHAT?
            }

            // Create DTO objects for this environment.
            Design design = new Design(instance, assembly);
            Transition transition = new Transition(instance, assembly);

            if( StringUtils.isEmpty(env)) {
                // If we are targeting an assembly we need to gather computes across all environments
                List<CiResource> environments = transition.listEnvironments();
                for (CiResource environment : environments) {
                    gatherEnvironmentOneOpsData(instance, assembly, environment.getCiName(), design, transition, environment);
                }
            } else {
                // If we're targeting an environment we only need to gather computes for one environment
                gatherEnvironmentOneOpsData(instance, assembly, env, design, transition, transition.getEnvironment(env));
            }

        } catch ( OneOpsClientAPIException e ) {
            // TODO: Better error handling please.
            e.printStackTrace();
            System.err.println( "Error interacting with OneOps" );
        }
    }

    private void gatherEnvironmentOneOpsData(OOInstance instance, String assembly, String env, Design design, Transition transition, CiResource environment) throws OneOpsClientAPIException, InventoryException {
        Operation operation = new Operation(instance, assembly, environment.getCiName());
        List<CiResource> platforms = transition.listPlatforms(env);
        globalVarsMap.put(environment, transition.listGlobalVariables(environment.getCiName()));

        for (CiResource platform : platforms) {
            // Gather a list of components in the platform.
            List<CiResource> components = transition.listPlatformComponents(env,platform.getCiName());

            envByPlatformMap.put(platform, environment);

            // Create a Map of CiResource instances indexed by components and instance name.
            gatherInstanceMapsByComponentName(operation, environment, platform, components);
            platformVarsMap.put(platform, transition.listPlatformVariables(environment.getCiName(), platform.getCiName()));

            // Retrieve all hosts from these platforms
            gatherAllHosts(operation,
                    environment,
                    platform,
                    components);
        }
    }

    /**
     * Note this is possibly a misnomer - this gathers all the hosts, but it also drops each host into
     * a series of Ansible group buckets as defined in the specification.
     *
     * @param operation OO API Operation object
     * @param environment CiResource corresponding to the environment
     * @param platform CiResource corresponding to the platform
     * @param components A list of  CiResources for a compute
     * @throws OneOpsClientAPIException
     */
    private void gatherAllHosts(Operation operation,
                                CiResource environment,
                                CiResource platform,
                                List<CiResource> components)
            throws OneOpsClientAPIException, InventoryException {
        for( CiResource component : components ) {

            // GOTCHA: To identify computes we're checking the Class Name of the transition component.
            // If it contains ".Compute" then we assume this is a host for Ansible.
            if( component.getCiClassName().contains(".Compute") ) {
                List<CiResource> computes = operation.listInstances( platform.getCiName(), component.getCiName() );

                // Add CiResources to all hosts
                allHosts.addAll( computes );

                // Add CiResources to the envHosts
                if(!envHosts.containsKey(environment)) {
                    envHosts.put(environment, new ArrayList<CiResource>());
                }
                envHosts.get(environment).addAll(computes);

                // Add CiResources to the platformHosts
                if(!platformHosts.containsKey(platform)) {
                    platformHosts.put(platform, new ArrayList<CiResource>());
                }
                platformHosts.get(platform).addAll(computes);

                /**
                 * Add CiResources to the platformComputeHosts
                 *
                 * This is here to expose Ansible groups at the sub-platform level.  Specifically to enable teams like
                 * BFD that have multiple compute component types.
                 */
                PlatformCompute platformCompute = new PlatformCompute(platform, component.getCiName());
                if( !platformComputeHosts.containsKey( platformCompute ) ) {
                    platformComputeHosts.put(platformCompute, new ArrayList<CiResource>());
                }
                platformComputeHosts.get(platformCompute).addAll(computes);

                for( CiResource compute : computes ) {
                    // This relates the operations instance of each component to the
                    // transition component for the compute
                    computeComponent.put( compute, component );

                    // Add to hostsById
                    // GOTCHA: OneOps has two IP addresses public and private.  I've
                    //         made an assumption that the public_id address is what we
                    //         are using for Ansible.
                    String hostId = computeHostId(compute);
                    hostsById.put( hostId, compute );

                    // Two annoying maps that let us look up the corresponding env and platform for a compute
                    envByHostMap.put( compute, environment );
                    platformByHostMap.put( compute, platform );
                }

            }
        }
    }

    /**
     * This method iterates through all of the component instances (for everything, not just computes) and
     * it creates a collection of CiResources for operations instances indexed by the component name and
     * the "instance name" (see below)
     *
     * To understand this, you should understand that operations instances of components
     * tend to come in groups.  There is a "compute-1111232-2" that is related to "hostname-1111232-2"
     * both of which are related to a "os-1111232-2."   You will also need to understand how
     * "extractInstanceNum" works.
     *
     * Warning, this method is confusing.  We need it because the CiResource for a compute instance in operations
     * does not contain a reference to related instances.
     *
     * @param operation
     * @param platform
     * @param components
     * @throws OneOpsClientAPIException
     */
    private void gatherInstanceMapsByComponentName(Operation operation, CiResource environment, CiResource platform, List<CiResource> components) throws OneOpsClientAPIException {
        // Gather Hostnames by IP Address
        for( CiResource component : components ) {
            String compositeCiName = environment.getCiName() + ":" + platform.getCiName() + ":" + component.getCiName();
            Map<String,CiResource> instancesByInstanceNum;
            if( instanceMapsByComponentName.containsKey( compositeCiName ) ) {
                instancesByInstanceNum = instanceMapsByComponentName.get( compositeCiName );
            } else {
                instancesByInstanceNum = new HashMap<String,CiResource>();
                instanceMapsByComponentName.put( compositeCiName, instancesByInstanceNum );
            }
            try {
                List<CiResource> componentInstances = operation.listInstances(platform.getCiName(), component.getCiName());
                for (CiResource componentInstance : componentInstances) {

                    // Ugly - have to parse the name of the component to get the instance number.
                    String instanceNum = extractInstanceNum(componentInstance);
                    instancesByInstanceNum.put(instanceNum, componentInstance);

                }
            } catch(OneOpsClientAPIException e) {
                //System.err.println( "Error fetching instances of " + component.getCiName());

            }
        }
    }

    /**
     * Generate the JSONObject for the Ansible Dynamic Inventory List.
     * @return JSONObject populated with dynamic inventory for the --list argument
     * @throws InventoryException
     */
    public JSONObject generateList() throws InventoryException {
        JSONObject json = new JSONObject();

        // Generate Host Vars for all Computes
        JSONObject meta = new JSONObject();
        json.put("_meta", meta );
        generateHostvars(meta);
        generateOOGroup(json);

        generateEnvironmentGroups(json);
        generatePlatformGroups(json);
        generatePlatformComputeGroups(json);
        return json;
    }

    /**
     * Generate the JSONObject for the Ansible Dynamic Inventory Host
     * @return JSONObject populated with dynamic inventory for the --host ip argument
     * @throws InventoryException
     */
    public JSONObject generateHost(String ipAddress) throws InventoryException {
        JSONObject json = new JSONObject();

        /**
         * Yes, this is inefficient.  I wish there was a better way to query for an individual host, but
         * Ansible inventories from cloud providers are designed to be cached.  We should build a quick caching
         * layer so we don't kill the OneOps API.  (This is common for other public cloud Ansible integrations)
         */
        for( CiResource host : allHosts ) {
            JSONObject hostObj = new JSONObject();

            String hostId = computeHostId(host);
            if (StringUtils.equals(hostId.trim(), ipAddress)) {
                generateHostJson(host, json);
            }
        }

        return json;
    }

    /**
     * Add platform compute Ansible groups to the List JSON - these are groups that are relevant for assemblies
     * that define multiple compute components in a single platform (a rare, but important usecase)
     *
     * @param json
     */
    private void generatePlatformComputeGroups(JSONObject json) throws InventoryException {
        // Generate the platform compute groups
        for(PlatformCompute platformCompute : platformComputeHosts.keySet()) {
            JSONArray platComp = new JSONArray();
            for(CiResource host : platformComputeHosts.get(platformCompute) ) {
                String publicIp = computeHostId(host);
                platComp.put( publicIp );
            }

            String groupIdentifier = "platform-" + platformCompute.getPlatform().getCiName() + "-" + platformCompute.getComputeType();
            if( StringUtils.isEmpty( this.env ) ) {
                groupIdentifier = "env-" + envByPlatformMap.get( platformCompute.getPlatform() ).getCiName() + "-" + groupIdentifier;
            }

            json.put(groupIdentifier, platComp);
        }
    }

    /**
     * Add platform Ansible groups for the List JSON.
     *
     * @param json
     */
    private void generatePlatformGroups(JSONObject json) throws InventoryException {
        // Generate the platform groups
        for(CiResource platform : platformHosts.keySet()) {
            CiResource environment = envByPlatformMap.get(platform);

            JSONObject plat = new JSONObject();
            JSONArray hosts = new JSONArray();
            for(CiResource host : platformHosts.get(platform) ) {
                String publicIp = computeHostId(host);
                hosts.put( publicIp );
            }
            plat.put("hosts", hosts);

            JSONObject vars = new JSONObject();
            addGlobalVariables(environment, vars );
            addPlatformVariables(platform, vars);

            String compositeCiNameFqdn = environment.getCiName() + ":" + platform.getCiName() + ":fqdn";
            // If there is a hostname component, grab the hostnames for this compute instance.
            if( instanceMapsByComponentName.containsKey(compositeCiNameFqdn)) {

                if( instanceMapsByComponentName.containsKey(compositeCiNameFqdn) ) {
                    Map<String, CiResource> fqdnByInstanceNum = instanceMapsByComponentName.get(compositeCiNameFqdn);

                    if( fqdnByInstanceNum.values() != null && fqdnByInstanceNum.values().size() > 0 ) {
                        // Get the first FQDN resource in the platform
                        CiResource fqdn = (CiResource) fqdnByInstanceNum.values().toArray()[0];
                        Map<String, Object> fqdnProperties = fqdn.getCiAttributes().getAdditionalProperties();

                        // Populate the Short Aliases for the FQDN as a Groupvar
                        if (!StringUtils.isEmpty((String) fqdnProperties.get("aliases"))) {
                            vars.put("fqdn_aliases", new JSONArray((String) fqdnProperties.get("aliases")));
                        }

                        // Populate the Full Aliases for the FQDN as a Groupvar
                        if (!StringUtils.isEmpty((String) fqdnProperties.get("full_aliases"))) {
                            vars.put("fqdn_full_aliases", new JSONArray((String) fqdnProperties.get("full_aliases")));
                        }
                    }
                }
            }


            plat.put("vars", vars);

            String groupIdentifier = "platform-" + platform.getCiName();
            if( StringUtils.isEmpty( this.env ) ) {
                groupIdentifier = "env-" + envByPlatformMap.get( platform ).getCiName() + "-" + groupIdentifier;
            }

            json.put(groupIdentifier, plat );
        }
    }

    /**
     * Add Environment Ansible groups for the List JSON.
     *
     * Note that we define a few group variables for environments.  Availability, profile, etc.
     *
     * @param json
     */
    private void generateEnvironmentGroups(JSONObject json) throws InventoryException {
        // Generate the environment group
        for(CiResource environment : envHosts.keySet()) {
            JSONObject env = new JSONObject();
            JSONArray hosts = new JSONArray();
            for(CiResource host : envHosts.get(environment) ) {
                String publicIp = computeHostId(host);
                hosts.put( publicIp );
            }
            env.put("hosts", hosts);

            Map ciAddlProps = environment.getCiAttributes().getAdditionalProperties();
            JSONObject vars = new JSONObject();
            vars.put("oo_env_id", environment.getCiId());
            vars.put("oo_env_name", environment.getCiName());
            vars.put("oo_env_namespace", environment.getNsPath());
            vars.put("oo_env_profile", ciAddlProps.get("profile") );
            vars.put("oo_env_availability", ciAddlProps.get("availability") );

            addGlobalVariables( environment, vars );

            env.put("vars", vars);

            json.put("env-" + environment.getCiName(), env );
        }
    }

    /**
     * Generate the "OO" group which contains all hosts.
     *
     * @param json
     */
    private void generateOOGroup(JSONObject json) throws InventoryException {
        // Generate the oo group with all hosts
        JSONObject ooGroup = new JSONObject();
        JSONArray ooHosts = new JSONArray();
        for(CiResource host : allHosts) {
            String publicIp = computeHostId(host);
            ooHosts.put( publicIp );
        }
        ooGroup.put("hosts", ooHosts);
        JSONObject ooVars = new JSONObject();
        ooGroup.put("vars", ooVars);
        json.put("oo", ooGroup);
    }

    /**
     * Generate all the hostvars in _meta for the List JSON.
     *
     * @param meta
     */
    private void generateHostvars(JSONObject meta) throws InventoryException {
        JSONObject hostvars = new JSONObject();
        meta.put("hostvars", hostvars);

        for( CiResource host : allHosts ) {
            JSONObject hostObj = new JSONObject();
            String publicIp = generateHostJson(host, hostObj);
            hostvars.put( publicIp, hostObj);
        }
    }

    /**
     * Generate hostvars.  This is used in both the List JSON output and the Host JSON output.
     *
     * Please note that this method is full of acrobatics.  If you follow how we jump to sibling instances of
     * hostname and os, you'll understand what's happening here.
     *
     * @param host
     * @param hostObj
     * @return
     */
    private String generateHostJson(CiResource host, JSONObject hostObj) throws InventoryException {
        Map ciAddlProps = host.getCiAttributes().getAdditionalProperties();

        // BIG ASSUMPTION: Public IP is what we use for host in Ansible.
        String hostId = computeHostId(host);

        String publicIp = (String) ciAddlProps.get("public_ip");
        hostObj.put("ansible_ssh_host", hostId );
        hostObj.put("oo_public_ip", publicIp );
        hostObj.put("oo_instance_name", ciAddlProps.get("instance_name"));
        hostObj.put("oo_instance_id", ciAddlProps.get("instance_id"));
        hostObj.put("oo_namespace", host.getNsPath());

        /**
         * BEGIN I HATE EVERYTHING ABOUT THIS
         *
         * There is a "metadata" property in additional properties that is
         * a string of JSON.  We have to parse it.
         *
         * I don't know if this the OneOps API packing a JSON document into a JSON string into a JSON document or
         * something else, but it's painful.  We should look at this.
         */
        String metadataStr = (String) ciAddlProps.get("metadata");
        JSONObject metadata = new JSONObject( metadataStr );

        hostObj.put("oo_organization", metadata.get("organization"));
        hostObj.put("oo_assembly", metadata.get("assembly"));
        hostObj.put("oo_environment", metadata.get("environment"));
        hostObj.put("oo_platform", metadata.get("platform"));
        hostObj.put("oo_owner", metadata.get("owner"));
        hostObj.put("oo_mgmt_url", metadata.get("mgmt_url"));
        hostObj.put("oo_component", metadata.get("component"));
        hostObj.put("oo_instance", metadata.get("instance"));
        /**
         * END I HATE EVERYTHING ABOUT THIS
         */


        hostObj.put("oo_compute_name", host.getCiName());
        hostObj.put("oo_component_name", computeComponent.get( host ).getCiName());

        // Adds the name of the cloud for this compute
        hostObj.put("oo_cloud", ((Map<String,String>) host.getAdditionalProperties().get("deployedTo")).get("ciName"));

        hostObj.put("oo_host_id", ciAddlProps.get("host_id"));
        hostObj.put("oo_hypervisor", ciAddlProps.get("hypervisor"));
        hostObj.put("oo_availability_zone", ciAddlProps.get("availability_zone"));
        hostObj.put("oo_instance_size", ciAddlProps.get("size"));
        hostObj.put("oo_num_cores", ciAddlProps.get("cores"));
        hostObj.put("oo_ram", ciAddlProps.get("ram"));
        hostObj.put("oo_server_image_name", ciAddlProps.get("server_image_name"));
        hostObj.put("oo_server_image_id", ciAddlProps.get("server_image_id"));
        hostObj.put("oo_private_ip", ciAddlProps.get("private_ip"));
        hostObj.put("oo_vm_state", ciAddlProps.get("vm_state"));

        // Get the instance number for this computer - we need this to jump to sibling instances for
        // hostname and os
        String instanceNum = extractInstanceNum(host);
        String environmentName = metadata.get("environment").toString();
        String platformName = metadata.get("platform").toString();

        String compositeCiNameHostname = environmentName + ":" + platformName + ":hostname";
        // If there is a hostname component, grab the hostnames for this compute instance.
        if( instanceMapsByComponentName.containsKey(compositeCiNameHostname)) {

            Map<String,CiResource> hostnamesByInstanceNum = instanceMapsByComponentName.get(compositeCiNameHostname);
            // Retrieve related hostname component (if there is one)
            if( hostnamesByInstanceNum.containsKey( instanceNum ) ) {
                CiResource hostname = hostnamesByInstanceNum.get( instanceNum );

                String entriesStr = (String) hostname.getCiAttributes().getAdditionalProperties().get("entries");
                JSONObject entries = new JSONObject(entriesStr);
                Set<String> hosts = entries.keySet();
                hosts.removeIf(name -> name.matches(IPV4_REGEX));
                hostObj.put("oo_hostnames", hosts );
            }

        }

        String compositeCiNameOs = environmentName + ":" + platformName + ":os";
        // If there is an OS, grab the OS name and type for this compute instance.
        if( instanceMapsByComponentName.containsKey(compositeCiNameOs)) {

            Map<String,CiResource> osByInstanceNum = instanceMapsByComponentName.get(compositeCiNameOs);
            // Retrieve related os component (if there is one)
            if( osByInstanceNum.containsKey( instanceNum ) ) {
                CiResource os = osByInstanceNum.get( instanceNum );
                hostObj.put("oo_os_type", os.getCiAttributes().getAdditionalProperties().get("ostype"));
                hostObj.put("oo_os_name", os.getCiAttributes().getAdditionalProperties().get("osname"));
            }

        }

        addGlobalVariables(envByHostMap.get(host), hostObj);
        addPlatformVariables(platformByHostMap.get(host), hostObj);

        return hostId;
    }

    /**
     * Get a CiName, and everything after the first "-" is the instance name.   So, the instance name for
     * "compute-1111223-2" is "1111223-2"
     *
     * I dislike this, but I found it necessary because the OO API doesn't give me the ability to traverse
     * sibling relationships in operations CiResources.
     *
     * @param ciResource
     * @return
     */
    private String extractInstanceNum(CiResource ciResource) {
        String ciName = ciResource.getCiName();
        return ciName.substring( ciName.indexOf('-') );
    }

    /**
     * Users can chose either "public_ip", "private_ip", or "hostname"
     *
     * Getting a public or private IP address is straightforward.  Getting a hostname requires a lot of
     * CiResource instance traversal.
     *
     * @param compute
     * @return
     */
    private String computeHostId(CiResource compute) throws InventoryException {
        Map ciAddlProps = compute.getCiAttributes().getAdditionalProperties();
        String hostId = null;

        if( this.hostMethod.equals("public_ip")) {
            hostId = (String) ciAddlProps.get("public_ip");
        } else if( this.hostMethod.equals("private_ip")) {
            hostId = (String) ciAddlProps.get("private_ip");
        } else if( this.hostMethod.equals("hostname")) {
            // Get the instance number for this computer - we need this to jump to sibling instances for
            // hostname and os
            String instanceNum = extractInstanceNum(compute);


            String metadataStr = (String) ciAddlProps.get("metadata");
            JSONObject metadata = new JSONObject( metadataStr );
            String environment = metadata.get("environment").toString();
            String platform = metadata.get("platform").toString();

            String compositeCiNameHostname = environment + ":" + platform + ":hostname";
            // If there is a hostname component, grab the hostnames for this compute instance.
            if( instanceMapsByComponentName.containsKey(compositeCiNameHostname)) {
                Map<String,CiResource> hostnamesByInstanceNum =
                        instanceMapsByComponentName.get(compositeCiNameHostname);
                // Retrieve related hostname component (if there is one)
                if( hostnamesByInstanceNum.containsKey( instanceNum ) ) {
                    CiResource hostname = hostnamesByInstanceNum.get( instanceNum );

                    String entriesStr = (String) hostname.getCiAttributes().getAdditionalProperties().get("entries");
                    JSONObject entries = new JSONObject(entriesStr);
                    ArrayList<String> hostnames = new ArrayList<String>();
                    hostnames.addAll( entries.keySet() );
                    // remove the IP if it's present -- an IP address should not be included as a hostname. 
                    hostnames.removeIf(host -> host.matches(IPV4_REGEX));
                    hostId = hostnames.get(0);
                }
            } else {
                throw new InventoryException("You've configured a host method of 'hostname', but this design doesn't have a hostname component");
            }
        }
        return hostId;
    }

    private void addCloudVariables(CiResource cloud, JSONObject hostObj) {
        List<CiResource> cloudVars = cloudVarsMap.get( cloud );
        JSONObject cloudVarsObj = new JSONObject();
        for( CiResource cloudVar : cloudVars ) {
            boolean secure = new Boolean( (String) cloudVar.getCiAttributes().getAdditionalProperties().get("secure") );
            String valueProp = secure ? "encrypted_value" : "value";
            cloudVarsObj.put( cloudVar.getCiName(),
                    (String) cloudVar.getCiAttributes().getAdditionalProperties().get( valueProp ) );
        }
        hostObj.put("cloud", cloudVarsObj );
    }

    private void addGlobalVariables(CiResource environment, JSONObject hostObj) {
        List<CiResource> globalVars = globalVarsMap.get( environment );
        JSONObject globalVarsObj = new JSONObject();
        for( CiResource globalVar : globalVars ) {
            boolean secure = new Boolean( (String) globalVar.getCiAttributes().getAdditionalProperties().get("secure") );
            String valueProp = secure ? "encrypted_value" : "value";
            globalVarsObj.put( globalVar.getCiName(),
                    (String) globalVar.getCiAttributes().getAdditionalProperties().get( valueProp ) );
        }
        hostObj.put("global", globalVarsObj );
    }

    private void addPlatformVariables(CiResource platform, JSONObject vars) {
        List<CiResource> platformVars = platformVarsMap.get( platform );
        JSONObject platformVarsObj = new JSONObject();
        for( CiResource platformVar : platformVars ) {
            boolean secure = new Boolean( (String) platformVar.getCiAttributes().getAdditionalProperties().get("secure") );
            String valueProp = secure ? "encrypted_value" : "value";
            platformVarsObj.put( platformVar.getCiName(),
                    (String) platformVar.getCiAttributes().getAdditionalProperties().get( valueProp ) );
        }
        vars.put("platform", platformVarsObj );
    }


}
