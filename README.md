# proxyserver

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.package.type=native
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.package.type=native -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/proxyserver-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/gradle-tooling.

## Provided Code

### RESTEasy Reactive

Easily start your Reactive RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

## Set up GSSAPI authentication method

To use the GSSAPI authentication method, you will need:

- [Key distribution center (KDC)](<#key-distribution-center-(kdc)>)
- [JAAS login configuration file](#jaas-login-configuration-file)
- [Keytab file](#keytab-file)

### Key distribution center (KDC)

To set up a KDC, follow this <a href="https://ubuntu.com/server/docs/service-kerberos" target="_blank">instruction</a>.

If the KDC runs locally, you can set up a record in `/etc/hosts` in order for the KDC's domain to point to local host:

```
127.0.0.1	kerberos.mit.edu // use your KDC's domain here instead
```

> I observed that using `curl` command will, by default, use principle `rcmd/0.0.0.0@ATHENA.MIT.EDU` (hence the example in [Keytab file](#keytab-file) section).

```bash
$ curl --socks5 0.0.0.0:1080 --ipv4 https://jsonplaceholder.typicode.com/todos/1 -v
*   Trying 0.0.0.0:1080...
* TCP_NODELAY set
* SOCKS5 communication to jsonplaceholder.typicode.com:443
* GSS-API error: gss_init_sec_context failed:
Unspecified GSS failure.  Minor code may provide more information.
Server rcmd/0.0.0.0@ATHENA.MIT.EDU not found in Kerberos database
* Failed to initial GSS-API token.
* Unable to negotiate SOCKS5 GSS-API context.
* Closing connection 0
curl: (7) GSS-API error: gss_init_sec_context failed:
Unspecified GSS failure.  Minor code may provide more information.
Server rcmd/0.0.0.0@ATHENA.MIT.EDU not found in Kerberos database
```

> So I had to manually add that principle, though no need to configure ACL permissions (in `/etc/krb5kdc/kadm5.acl`) for that principle.

### JAAS login configuration file

Create a file named `jaas.conf` with the following content:

```
com.sun.security.jgss.accept {
    com.sun.security.auth.module.Krb5LoginModule required storeKey=true // don't know why but storeKey=true makes things work
    doNotPrompt=true
    useKeyTab=true
    keyTab=<path/to/keytab/file> // why doesn't this work with relative file path?
    principal="rcmd/0.0.0.0@ATHENA.MIT.EDU"; // notice the semicolon after a LoginModule, https://docs.oracle.com/javase/8/docs/technotes/guides/security/jgss/tutorials/LoginConfigFile.html
};
```

Adjust `build.gradle` in order for java GSS API to load the jaas.conf:

```groovy
quarkusDev {
    jvmArgs = [
        '-Djavax.security.auth.useSubjectCredsOnly=false', // https://docs.oracle.com/javase/8/docs/technotes/guides/security/jgss/tutorials/BasicClientServer.html#useSub
        '-Djava.security.auth.login.config=<path/to/jaas.conf>' // https://docs.oracle.com/javase/8/docs/technotes/guides/security/jgss/tutorials/BasicClientServer.html#TheLCF (don't know why relative file path doesn't work)
    ]
}
```

### Keytab file

First, we need to know what encryption algoritms are use by what principle:

```bash
$ klist -e
Ticket cache: FILE:/tmp/krb5cc_1000
Default principal: ...

Valid starting       Expires              Service principal
...
09/03/2024 10:05:03  10/03/2024 10:03:08  rcmd/0.0.0.0@ATHENA.MIT.EDU
        Etype (skey, tkt): aes256-cts-hmac-sha1-96, aes256-cts-hmac-sha1-96
...
```

Next, create a key tab file named `key.keytab` storing principle `rcmd/0.0.0.0@ATHENA.MIT.EDU`'s key, key version number `1` and encryption algorithm `aes256-cts-hmac-sha1-96` using `ktutil` command:

```bash
$ ktutl
ktutil:  addent -password -p rcmd/0.0.0.0@ATHENA.MIT.EDU -k 1 -e aes256-cts-hmac-sha1-96
Password for rcmd/0.0.0.0@ATHENA.MIT.EDU:
ktutil:  wkt keytab
ktutil:  q
```

See the keytab content:

```bash
$ klist -k key.keytab
Keytab name: FILE:keytab
KVNO Principal
---- --------------------------------------------------------------------------
   1 rcmd/0.0.0.0@ATHENA.MIT.EDU
```
