# suid-server-java v0.9.11
Suid-server implementation for the Java EE technology stack.<br>
http://download.github.io/suid-server-java/

Suids are distributed Service-Unique IDs that are short and sweet.<br>
See the main [project](https://download.github.io/suid/) for details.

## Download
* [suid-server-java-0.9.11.jar](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.9.11/suid-server-java-0.9.11.jar) ([signature](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.9.11/suid-server-java-0.9.11.jar.asc))
* [suid-server-java-0.9.11-sources.jar](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.9.11/suid-server-java-0.9.11-sources.jar) ([signature](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.9.11/suid-server-java-0.9.11-sources.jar.asc))
* [suid-server-java-0.9.11-javadoc.jar](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.9.11/suid-server-java-0.9.11-javadoc.jar) ([signature](http://search.maven.org/remotecontent?filepath=ws/suid/suid-server-java/0.9.11/suid-server-java-0.9.11-javadoc.jar.asc))

## Maven coordinates:
```xml
<dependency>
	<groupId>ws.suid</groupId>
	<artifactId>suid-server-java</artifactId>
	<version>0.9.11</version>
</dependency>
```
## Usage
* [Create a MySQL database and user](#create-a-mysql-database-and-user)
* [Create the suid table](#create-the-suid-table)
* [Insert the first record in the new table](#insert-the-first-record-in-the-new-table)
* [Configure a MySQL datasource](#configure-a-mysql-datasource)
* [Add the jar to WEB-INF/lib](#add-the-jar-to-web-inf-lib)
* [Add a servlet definition and mapping to web.xml](#add-a-servlet-definition-and-mapping-to-web-xml)

### Create a MySQL database and user
```sql
CREATE SCHEMA IF NOT EXISTS `suiddb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'username'@'hostname' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON `suiddb`.* TO 'username'@'hostname';
```
*NOTE*: Only MySQL is supported at the moment.

### Create the suid table
```sql
CREATE TABLE IF NOT EXISTS suid (
	block BIGINT NOT NULL AUTO_INCREMENT,
	shard TINYINT NOT NULL,
	PRIMARY KEY (block),
	UNIQUE KEY shard (shard)
);
```

### Insert the first record in the new table
```sql
INSERT INTO suid(shard) VALUES(0);
```
*Note*: This configures the shard ID as 0.<br> 
*See*: [Sharding](#sharding) [Using the database](#using-the-database)

### Configure a MySQL datasource
Using the tools for your preferred server, add a new DataSource called `SuidRWDS` that can connect to the database we just created.
For example, using JBoss CLI it could look like this:
	jboss-cli -c "data-source add --name=SuidRWDS --jndi-name=java:/jdbc/SuidRWDS --driver-name=mysql --connection-url=jdbc:mysql://hostname:3306/suiddb --user-name=username --password=password"

### Add the jar to WEB-INF/lib
Download or build the jar and add it to your web application's `WEB-INF/lib` folder.

### Add a servlet definition and mapping to web.xml
```xml
<servlet>
	<description></description>
	<display-name>SuidServlet</display-name>
	<servlet-name>SuidServlet</servlet-name>
	<servlet-class>ws.suid.SuidServlet</servlet-class>
	<init-param>
		<description>
			Shard number to use when no shard number is in the `suid` table yet. Will
			be ignored if there is already a record in the suid table. When supplied it
			should be in the range from 0 .. 3.
		</description>
		<param-name>shard</param-name>
		<param-value>0</param-value>
	</init-param>
</servlet>
<servlet-mapping>
	<servlet-name>SuidServlet</servlet-name>
	<url-pattern>/suid/suid.json</url-pattern>
</servlet-mapping>
```

## Sharding
To prevent a single point of failure, the total ID space for each domain is divided into 4 sections (called shards). 
This allows you to set up separate servers with their own databases to give out ID blocks in their own shard. This way there can be up to 4 different suid servers for a single domain, each with their own database, and they don't need to communicate at all. All you need to do is make sure that each server is configured with it's own shard ID.

To configure the shard id of the server, there are two options:
1. [Using a servlet configuration parameter](#using-a-servlet-configuration-parameter)
2. [Using the database](#using-the-database)

### Using a servlet configuration parameter
This works by setting the servlet configuration parameter `shard` to the shard ID of the server you are configuring in your webapp's `web.xml` file. Refer to the previous section for an example. <br>
*NOTE*: This option only works if the suid table is still empty. If the suid table does not exist yet, it is created. <br>
*SEE*: [Add a servlet definition and mapping to web.xml](#add-a-servlet-definition-and-mapping-to-web-xml)

### Using the database
The preferred way of configuring the shard ID is by just inserting the first record in the suid table manually. The shard id is used when inserting new records so once the first record is there the server can figure out the rest by itself. All the servlet configuration parameter does is tell the SuidServlet to insert this first record if it's not yet there.<br>
*SEE*: [Insert the first record in the new table](#insert-the-first-record-in-the-new-table)

## Copyright
Copyright (c) 2015 by Stijn de Witt. Some rights reserved.

## License
Creative Commons Attribution 4.0 International (CC BY 4.0)
https://creativecommons.org/licenses/by/4.0/

