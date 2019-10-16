!/bin/bash

# standard bash error handling
set -o errexit;
set -o pipefail;
set -o nounset;
# debug commands
set -x;

# working dir to install binaries etc, cleaned up on exit
BIN_DIR="$(mktemp -d)"
# kind binary will be here
KIND="${BIN_DIR}/kind"

# cleanup on exit (useful for running locally)
cleanup() {
    "${KIND}" delete cluster || true
    rm -rf "${BIN_DIR}"
}
trap cleanup EXIT

# util to install a released kind version into ${BIN_DIR}
install_kind_release() {
    VERSION="v0.5.1"
    KIND_BINARY_URL="https://github.com/kubernetes-sigs/kind/releases/download/${VERSION}/kind-linux-amd64"
    wget -O "${KIND}" "${KIND_BINARY_URL}"
    chmod +x "${KIND}"
}

main() {
    # get kind
    install_kind_release
    # create a cluster
    "${KIND}" create cluster --loglevel=debug
    # set KUBECONFIG to point to the cluster
    KUBECONFIG="$("${KIND}" get kubeconfig-path)"
    export KUBECONFIG
    # TODO: invoke your tests here
    # teardown will happen automatically on exit

    curl -LO https://storage.googleapis.com/kubernetes-release/release/`curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt`/bin/linux/amd64/kubectl
    chmod +x ./kubectl
    sudo mv ./kubectl /usr/local/bin/kubectl
    kubectl version
    kubectl cluster-info

    docker run -it lalithsuresh/dcm-ci-latest "mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package && mvn -pl '!build-common' jacoco:report"
}

main
