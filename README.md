# Overview

The purpose of this project is to debug an LDAP/AD environment without the full Brinqa application.

# Requirements

* JDK 8+

# Getting Started

The project uses a standard Gradle build to the main application. See gradle docs for more details.

To see all the available tasks:
   
    $ ./gradlew tasks
    
Simple build of the distribution:

    $ ./gradlew assembleDist
      
# Install

Unzip the distribution to a directory from the project home this is `build/distributions`.


    $ unzip ldap-cli.zip
    $ cd ldap-cli
    $ ./bin/ldap-cli -h
    Options:
      -h, --help  Show this message and exit
    
    Commands:
      search  Search LDAP Directory

    $./bin/ldap-cli search -h
    Usage: app search [OPTIONS]
    
      Search LDAP Directory
    
    Options:
      --host TEXT          Hostname for the AD server
      --port INT           Port for the AD server
      --username TEXT      Username for the connection
      --password TEXT      Password for the connection
      --base-context TEXT  Base context for search.
      --page-size INT      Page size during the search
      --filter TEXT        Filter for search.
      -h, --help           Show this message and exit
    
