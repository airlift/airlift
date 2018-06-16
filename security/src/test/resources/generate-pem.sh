#!/bin/sh

set -eux

# create ca key
openssl genrsa -out rsa.ca.key 4096
openssl dsaparam -genkey -out dsa.ca.key 2048
openssl ecparam -name prime256v1 -genkey -out ec.ca.key

# create ca certificate
openssl req -new -x509 -days 9999 -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=RootCA" -key rsa.ca.key -out rsa.ca.crt
openssl req -new -x509 -days 9999 -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=RootCA" -key dsa.ca.key -out dsa.ca.crt
openssl req -new -x509 -days 9999 -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=RootCA" -key ec.ca.key -out ec.ca.crt

# create client key in pkcs1
openssl genrsa 4096 | grep "BEGIN RSA PRIVATE KEY" -A 100 > rsa.client.pkcs1.key
openssl dsaparam -genkey 2048 | grep "BEGIN DSA PRIVATE KEY" -A 100 > dsa.client.pkcs1.key
openssl ecparam -name prime256v1 -genkey | grep "BEGIN EC PRIVATE KEY" -A 100 > ec.client.pkcs1.key

# convert client key to pkcs8
openssl pkcs8 -topk8 -inform pem -outform pem -nocrypt -in rsa.client.pkcs1.key -out rsa.client.pkcs8.key
openssl pkcs8 -topk8 -inform pem -outform pem -nocrypt -in dsa.client.pkcs1.key -out dsa.client.pkcs8.key
openssl pkcs8 -topk8 -inform pem -outform pem -nocrypt -in ec.client.pkcs1.key -out ec.client.pkcs8.key

# convert client key to encrypted pkcs8
openssl pkcs8 -topk8 -inform pem -outform pem -passout pass:airlift -in rsa.client.pkcs8.key -out rsa.client.pkcs8.key.encrypted
openssl pkcs8 -topk8 -inform pem -outform pem -passout pass:airlift -in dsa.client.pkcs8.key -out dsa.client.pkcs8.key.encrypted
openssl pkcs8 -topk8 -inform pem -outform pem -passout pass:airlift -in ec.client.pkcs8.key -out ec.client.pkcs8.key.encrypted

# extract public key from private key
openssl rsa -pubout -inform pem -outform pem -in rsa.client.pkcs8.key -out rsa.client.pkcs8.pub
openssl dsa -pubout -inform pem -outform pem -in dsa.client.pkcs8.key -out dsa.client.pkcs8.pub
openssl ec -pubout -inform pem -outform pem -in ec.client.pkcs8.key -out ec.client.pkcs8.pub

# create client certificate request
openssl req -new -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=Server/CN=Test User" -passin pass:airlift -key rsa.client.pkcs8.key -out rsa.client.csr
openssl req -new -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=Server/CN=Test User" -passin pass:airlift -key dsa.client.pkcs8.key -out dsa.client.csr
openssl req -new -subj "/C=US/ST=CA/L=Palo Alto/O=Airlift/OU=Server/CN=Test User" -passin pass:airlift -key ec.client.pkcs8.key -out ec.client.csr

# create client certificate
openssl x509 -req -days 9999 -set_serial 01 -CA rsa.ca.crt -CAkey rsa.ca.key -in rsa.client.csr -out rsa.client.crt
openssl x509 -req -days 9999 -set_serial 01 -CA dsa.ca.crt -CAkey dsa.ca.key -in dsa.client.csr -out dsa.client.crt
openssl x509 -req -days 9999 -set_serial 01 -CA ec.ca.crt -CAkey ec.ca.key -in ec.client.csr -out ec.client.crt

# create pkcs1 pem files
cat rsa.ca.crt rsa.client.crt rsa.client.pkcs1.key > rsa.client.pkcs1.pem
cat dsa.ca.crt dsa.client.crt dsa.client.pkcs1.key > dsa.client.pkcs1.pem
cat ec.ca.crt ec.client.crt ec.client.pkcs1.key > ec.client.pkcs1.pem

# create pkcs8 pem files
cat rsa.ca.crt rsa.client.crt rsa.client.pkcs8.key.encrypted > rsa.client.pkcs8.pem.encrypted
cat dsa.ca.crt dsa.client.crt dsa.client.pkcs8.key.encrypted > dsa.client.pkcs8.pem.encrypted
cat ec.ca.crt ec.client.crt ec.client.pkcs8.key.encrypted > ec.client.pkcs8.pem.encrypted

# cleanup
rm rsa.ca.key
rm dsa.ca.key
rm ec.ca.key

rm rsa.client.csr
rm dsa.client.csr
rm ec.client.csr
