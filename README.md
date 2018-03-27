# OneOps Inventory

OneOps Inventory can generate a Ansible dynamic inventory for a OneOps assembly.
It can be executed against either a single environment or multiple environments
in a single assembly.


## Download and Install

To use OneOps-Inventory you download the executable JAR and place it in your
`$PATH`. Use the latest version available from
https://repo1.maven.org/maven2/com/oneops/oneops-inventory/

e.g.:

```
curl -o oo-inventory https://repo1.maven.org/maven2/com/oneops/oneops-inventory/0.1.1/oneops-inventory-0.1.1-uber.jar
chmod a+x oo-inventory
```

Now you can run the tool with

```
oo-inventory
```

Alternatively you can use

```
java -jar oo-inventory
```

## Configuration

Set four environment variables: you OO API token, Org, Assembly, and Env:

```
export OO_API_TOKEN="XYZ123USEYOUROWN"
export OO_ORG="devtools"
export OO_ASSEMBLY="website"
export OO_ENV="prod"
export OO_ENDPOINT="https://prod.oneops.com"
```

## Use the Inventory Tool with an Ansible Playbook

This repository contains a Python wrapper that can be called directly from
Ansible.

```
ansible-playbook -i ~/wherever/oo-wrapper.py yourplaybook.yml
```

Go forth and Ansible.

## Test the Inventory Tool

Run the Inventory tool:

```
oo-inventory --list
```

This generates _meta as well as hostvars. To retrieve a specific host:

```
oo-inventory --host 100.65.3.247
```

## Pointing to a different OneOps instance?

You can also define an OO_ENDPOINT environment variable if you need to point to
a different API endpoint.

```
export OO_ENDPOINT="http://whatever.oneops.instance.you.want/"
```

## Want Private IPs or Hostnames?

This inventory script assumes that you want public_ip addresses, but OneOps
keeps track of public and private IP addresses.  Also, if a design has a
hostname component each compute also has two hostnames.

To use OO public IP addresses set the OO_HOST_METHOD environment variable to
"public_ip".

```
export OO_HOST_METHOD="public_ip"
```

To use OO private IP addresses set the OO_HOST_METHOD environment variable to
"private_ip".

```
export OO_HOST_METHOD="private_ip"
```

To use OO hostnames set the OO_HOST_METHOD environment variable to "hostname".

```
export OO_HOST_METHOD="hostname"
```

If you use hostnames your Ansible dynamic inventory uses host names to
define groups, e.g.:


```
"platform-webserver-compute": [
  "webserver-198391581-1-224621269.prod.website.devtools.dal3.prod.oneops.com",
  "webserver-198391581-2-224621275.prod.website.devtools.dal3.prod.oneops.com",
  "webserver-198391587-2-224621357.prod.website.devtools.dfw3.prod.oneops.com",
  "webserver-198391587-1-224621351.prod.website.devtools.dfw3.prod.oneops.com"
]
```
	
## Building

```
mvn clean install
```
