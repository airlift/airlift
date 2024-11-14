#!/usr/bin/env bash


SOURCE=${BASH_SOURCE[0]}
while [ -L "$SOURCE" ]; do
  DIR=$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )
  SOURCE=$(readlink "$SOURCE")
  [[ $SOURCE != /* ]] && SOURCE=$DIR/$SOURCE
done

DIR=$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )

cd "${DIR}" || exit

OUTPUT_DIR="../../../target/binaries/"
EXECUTABLE_NAME="launcher"

go get launcher

echo "Output directory: ${DIR}/${OUTPUT_DIR}"

build_for_os_and_arch() {
    os="${1}"
    arch="${2}"
    path="${1}-${2}"

  echo "Building for ${os}/${arch} into ${path}/${EXECUTABLE_NAME}"
  CGO_ENABLED=0 GOOS="${os}" GOARCH="${arch}" go build -ldflags="-s -w -extldflags=-static -X launcher/args.Version=$(git describe HEAD)" -o "${DIR}/${OUTPUT_DIR}${path}/${EXECUTABLE_NAME}" .

  echo "Compressing binary with upx"
  if [[ "${os}" == "darwin" ]]; then
    echo "Skipping upx for MacOS"
  else
    upx "${DIR}/${OUTPUT_DIR}${path}/${EXECUTABLE_NAME}"
  fi

  echo "Done building for ${os}/${arch}, size: $(du -sh "${DIR}/${OUTPUT_DIR}${path}/${EXECUTABLE_NAME}" | cut -f1)"
}

build_for_os_and_arch "linux" "amd64"
build_for_os_and_arch "linux" "arm64"
build_for_os_and_arch "linux" "ppc64le"
build_for_os_and_arch "darwin" "arm64"
build_for_os_and_arch "darwin" "amd64"
