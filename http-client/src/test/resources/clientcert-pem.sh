#!/bin/sh

set -eux

# create CA
openssl genrsa 4096 | openssl pkcs8 -v1 PBE-SHA1-3DES -topk8 -inform pem -outform pem -nocrypt -out ca.key
openssl req -new -x509 -days 9999 -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=RootCA" -key ca.key -out ca.crt

# create server key
openssl genrsa 4096 | openssl pkcs8 -v1 PBE-SHA1-3DES -topk8 -inform pem -outform pem -nocrypt -out server.key
openssl req -new -key server.key -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=Server/CN=localhost" -out server.csr
openssl x509 -req -days 9999 -in server.csr -CA ca.crt -CAkey ca.key -set_serial 01 -out server.crt

# create server keystore
openssl pkcs12 -name server -inkey server.key -in server.crt -export -passout pass:changeit -out server.keystore
keytool -import -noprompt -alias ca -file ca.crt -storetype pkcs12 -storepass changeit -keystore server.keystore

# create client key
openssl genrsa 4096 | openssl pkcs8 -v1 PBE-SHA1-3DES -topk8 -inform pem -outform pem -passout pass:changeit -out client.key
openssl req -new -key client.key -passin pass:changeit -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=Client/CN=testing" -out client.csr
openssl x509 -req -days 9999 -in client.csr -CA ca.crt -CAkey ca.key -set_serial 02 -out client.crt
cat client.crt client.key > client.pem
