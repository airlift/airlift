#!/bin/sh

set -eux

# create keys
openssl req -new -x509 -nodes -keyout single.key -days 3560 -out single.crt -config single.conf
openssl req -new -x509 -nodes -keyout localhost.key -days 3560 -out localhost.crt -config localhost.conf
openssl req -new -x509 -nodes -keyout 127.0.0.1.key -days 3560 -out 127.0.0.1.crt -config 127.0.0.1.conf

# covert to PKCS #12
openssl pkcs12 -name single -inkey single.key -in single.crt -export -passout pass:airlift -out single.p12
openssl pkcs12 -name localhost -inkey localhost.key -in localhost.crt -export -passout pass:airlift -out localhost.p12
openssl pkcs12 -name local_ip -inkey 127.0.0.1.key -in 127.0.0.1.crt -export -passout pass:airlift -out 127.0.0.1.p12

# merge into a single PKCS #12
keytool -importkeystore -srckeystore single.p12 -destkeystore server.p12 -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass airlift -deststorepass airlift
keytool -importkeystore -srckeystore localhost.p12 -destkeystore server.p12 -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass airlift -deststorepass airlift
keytool -importkeystore -srckeystore 127.0.0.1.p12 -destkeystore server.p12 -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass airlift -deststorepass airlift
