#!/bin/sh

set -eux

# create keys
openssl req -new -x509 -nodes -keyout single13.key -days 3560 -out single13.crt -config single13.conf
openssl req -new -x509 -nodes -keyout single24.key -days 3560 -out single24.crt -config single24.conf
openssl req -new -x509 -nodes -keyout localhost.key -days 3560 -out localhost.crt -config localhost.conf

# convert to PKCS #12
openssl pkcs12 -name single13 -inkey single13.key -in single13.crt -export -passout pass:airlift -out single13.p12
openssl pkcs12 -name single24 -inkey single24.key -in single24.crt -export -passout pass:airlift -out single24.p12
openssl pkcs12 -name localhost -inkey localhost.key -in localhost.crt -export -passout pass:airlift -out localhost.p12
rm -f single13.key single13.crt single24.key single24.crt localhost.key localhost.crt

# merge into a combined PKCS #12
rm -f server.p12
keytool -importkeystore -srckeystore single13.p12 -destkeystore server.p12 -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass airlift -deststorepass airlift
keytool -importkeystore -srckeystore single24.p12 -destkeystore server.p12 -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass airlift -deststorepass airlift
keytool -importkeystore -srckeystore localhost.p12 -destkeystore server.p12 -srcstoretype PKCS12 -deststoretype PKCS12 -srcstorepass airlift -deststorepass airlift
rm -f single13.p12 single24.p12 localhost.p12
