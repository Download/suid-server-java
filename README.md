# suid-server-java v0.10.0
Suid-server implementation for the Java EE technology stack.<br>
http://download.github.io/suid-server-java/

Suids are distributed Service-Unique IDs that are short and sweet.<br>
See the main [project](https://download.github.io/suid/) for details.

## Download
* [suid-server-java-0.10.0.jar](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.10.0/suid-server-java-0.10.0.jar) ([signature](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.10.0/suid-server-java-0.10.0.jar.asc))
* [suid-server-java-0.10.0-sources.jar](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.10.0/suid-server-java-0.10.0-sources.jar) ([signature](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.10.0/suid-server-java-0.10.0-sources.jar.asc))
* [suid-server-java-0.10.0-javadoc.jar](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.10.0/suid-server-java-0.10.0-javadoc.jar) ([signature](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.10.0/suid-server-java-0.10.0-javadoc.jar.asc))

## Maven coordinates:
```xml
<dependency>
	<groupId>ws.suid</groupId>
	<artifactId>suid-server-java</artifactId>
	<version>0.10.0</version>
</dependency>
```
## Usage
* [Create a database and user](#create-a-database-and-user)
* [Create the suid table](#create-the-suid-table)
* [Insert the first record in the new table](#insert-the-first-record-in-the-new-table)
* [Configure a datasource](#configure-a-datasource)
* [Add the jar to WEB-INF/lib](#add-the-jar-to-web-inf-lib)
* [Add SuidRecord to persistence.xml](#add-suidrecord-to-persistence-xml)
* [Optional: Add a servlet mapping to web.xml](#optional-add-a-servlet-mapping-to-web-xml)

### Create a database and user
In the examples below we are assuming a MySQL database, but any database that supports 
JPA2 can be used.
```sql
CREATE SCHEMA IF NOT EXISTS `suiddb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'username'@'hostname' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON `suiddb`.* TO 'username'@'hostname';
```

### Create the suid table
```sql
CREATE TABLE IF NOT EXISTS suid (
	block BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	shard TINYINT NOT NULL
);
```

### Insert the first record in the new table
```sql
INSERT INTO suid(shard) VALUES(0);
```
*Note*: This configures the shard ID as 0.<br> 
*See*: [Sharding](#sharding) [Configure sharding](#configure-sharding)

### Configure a datasource
Using the tools for your preferred server, add a new DataSource that can connect to the database we just created. In the examples below we are configuring a MySQL database on JBoss/WildFly, but all
database and EE vendors that support JPA2 can be used.

#### Using JBoss CLI
	jboss-cli -c "data-source add --name=MyDataSource --jndi-name=java:/jdbc/MyDataSource --driver-name=mysql --connection-url=jdbc:mysql://hostname:3306/suiddb --user-name=username --password=password"

#### Using standalone.xml
```xml
<datasource jndi-name="java:/jdbc/MyDataSource" pool-name="MyDataSource" enabled="true" use-java-context="true" use-ccm="true">
    <connection-url>jdbc:mysql://localhost:3306/suiddb</connection-url>
    <driver>mysql</driver>
    <pool><flush-strategy>IdleConnections</flush-strategy></pool>
    <security>
        <user-name>root</user-name>
        <password>secret</password>
    </security>
    <validation>
        <check-valid-connection-sql>SELECT 1</check-valid-connection-sql>
        <background-validation>true</background-validation>
        <background-validation-millis>60000</background-validation-millis>
    </validation>
</datasource>

### Add the jar to WEB-INF/lib
If you build your webapp with Maven, add a dependency using the Maven coordinates mentioned above. Otherwise, copy `suid-server-java-0.10.0.jar` to your `WEB-INF/lib` folder.

### Add SuidRecord to persistence.xml
Suid-server-java uses JPA, so make sure JPA can find it by adding the `SuidRecord` class
to your webapp's `persistence.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence version="2.1" xmlns="http://xmlns.jcp.org/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence
             http://xmlns.jcp.org/xml/ns/persistence/persistence_2_1.xsd">
	<persistence-unit name="MyPersistenceUnit">
		<description>JPA Persistence Unit.</description>
		<jta-data-source>jdbc/MyDataSource</jta-data-source>
		<exclude-unlisted-classes>false</exclude-unlisted-classes>
		<class>ws.suid.SuidRecord</class>
	</persistence-unit>
</persistence>
```
Note the `class` element. I always add `exclude-unlisted-classes` as well and set it
to `false`, but this is not needed for suid-server-java.

### Optional: Add a servlet mapping to web.xml
Suid-server-java includes a servlet named `SuidServlet` that will register itself to listen
for requests on url `/suid/suid.json`. If you want to map it to a different url (or otherwise override it's configuration) you can configure it in `web.xml` like you can with any other servlet. Use `servlet-name` = `ws.suid.SuidServlet` to *override* the default configuration, or a different servlet name to *augment* it.

```xml
<servlet-mapping>
	<servlet-name>ws.suid.SuidServlet</servlet-name>
	<url-pattern>/super-suid/suid.json</url-pattern>
</servlet-mapping>
```
In the example above we specified a matching servlet name, so this *overrides* the default configuration. SuidServlet will be listening to one url:
* `/super-suid/suid.json` (overridden)

```xml
<servlet-mapping>
	<servlet-name>SuperSuidServlet</servlet-name>
	<url-pattern>/super-suid/suid.json</url-pattern>
</servlet-mapping>
```
In this example we specified a different servlet name, so this *augments* the default configuration. SuidServlet will be listening to two urls:
* `/suid/suid.json` (default) 
* `/super-suid/suid.json` (augmented)


## Sharding
To prevent a single point of failure, the total ID space for each domain is divided into 2 sections (called shards). 
This allows you to set up separate servers with their own databases to give out ID blocks in their own shard. This way there can be two different suid servers for a single domain, each with their own database, and they don't need to communicate at all. All you need to do is make sure that each server is configured with it's own shard ID, `0` or `1`.

### Configure sharding
To configure the shard ID we insert the first record in the suid table manually. The shard id on the existing record is used when inserting new records so once the first record is there the server can figure out the rest by itself. If we don't insert a first record, the shard ID defaults to `0`.
*SEE*: [Insert the first record in the new table](#insert-the-first-record-in-the-new-table)

## Copyright
Copyright (c) 2015 by [Stijn de Witt](http://StijnDeWitt.com). Some rights reserved.

## License
Creative Commons Attribution 4.0 International (CC BY 4.0)
https://creativecommons.org/licenses/by/4.0/

