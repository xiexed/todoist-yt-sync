# For JCliff (currently it is actually not used)

    ansible-galaxy install -r requirements.yml 

    ansible-galaxy collection install wildfly.jcliff

# Just deploy on installed Wildfly

    ansible-playbook -i inventories/uits-labs/inventory.yaml  playbook-deploy.yaml