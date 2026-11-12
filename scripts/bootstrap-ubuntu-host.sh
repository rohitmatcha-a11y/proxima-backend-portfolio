#!/usr/bin/env bash
# Prepares an Ubuntu production host for the Proxima Docker stack.
#
# Prefer this script as EC2 user data at launch, or run it once as root through
# AWS Systems Manager Session Manager. It deliberately does not clone code,
# create secrets, change AWS security groups, open ports, or start Proxima.

set -Eeuo pipefail

readonly DEPLOY_ROOT="${JOBZ_DEPLOY_ROOT:-/opt/jobz}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

if [[ "${EUID}" -ne 0 ]]; then
  fail "Run this script with sudo or as root."
fi

if [[ ! -r /etc/os-release ]]; then
  fail "Cannot identify the operating system."
fi

# shellcheck disable=SC1091
source /etc/os-release
if [[ "${ID}" != "ubuntu" ]]; then
  fail "This script supports Ubuntu hosts only; found '${ID}'."
fi

if [[ "${VERSION_ID}" != "24.04" ]]; then
  fail "This script supports Ubuntu 24.04 only; found '${VERSION_ID}'."
fi

export DEBIAN_FRONTEND=noninteractive

apt_retry() {
  local attempt
  for attempt in {1..4}; do
    if apt-get "$@"; then
      return 0
    fi

    if [[ "${attempt}" -lt 4 ]]; then
      printf 'apt-get failed; retrying in %ss...\n' "$((attempt * 5))" >&2
      sleep "$((attempt * 5))"
    fi
  done

  fail "apt-get $* failed after four attempts."
}

install_aws_cli_v2() {
  local aws_arch
  local tmpdir
  local -a installer_args=()

  case "$(dpkg --print-architecture)" in
    amd64) aws_arch="x86_64" ;;
    arm64) aws_arch="aarch64" ;;
    *) fail "AWS CLI v2 installer is unsupported on architecture '$(dpkg --print-architecture)'." ;;
  esac

  tmpdir="$(mktemp -d)"

  if ! curl --fail --show-error --silent --location --retry 4 --retry-delay 2 \
    --proto '=https' --tlsv1.2 \
    "https://awscli.amazonaws.com/awscli-exe-linux-${aws_arch}.zip" \
    -o "${tmpdir}/awscliv2.zip"; then
    rm -rf "${tmpdir}"
    fail "Could not download the AWS CLI v2 installer."
  fi

  if ! unzip -q "${tmpdir}/awscliv2.zip" -d "${tmpdir}"; then
    rm -rf "${tmpdir}"
    fail "Could not extract the AWS CLI v2 installer."
  fi

  if [[ -d /usr/local/aws-cli || -e /usr/local/bin/aws ]]; then
    installer_args+=(--update)
  fi

  if ! "${tmpdir}/aws/install" --install-dir /usr/local/aws-cli --bin-dir /usr/local/bin "${installer_args[@]}"; then
    rm -rf "${tmpdir}"
    fail "AWS CLI v2 installation failed."
  fi

  rm -rf "${tmpdir}"
  command -v aws >/dev/null || fail "AWS CLI was not installed."
}

printf '%s\n' 'Installing Docker Engine, Docker Compose, AWS CLI, and host utilities...'
apt_retry update
apt_retry install --yes ca-certificates curl git jq unzip
install_aws_cli_v2

install -m 0755 -d /etc/apt/keyrings
curl --fail --show-error --silent --location --retry 4 --retry-delay 2 \
  https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
  "$(dpkg --print-architecture)" "${VERSION_CODENAME}" \
  > /etc/apt/sources.list.d/docker.list

apt_retry update
apt_retry install --yes docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# Update Docker only during an intentional maintenance window.
apt-mark hold docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

printf '%s\n' 'Applying the persistent Elasticsearch kernel setting...'
printf 'vm.max_map_count = 262144\n' > /etc/sysctl.d/99-jobz-elasticsearch.conf
sysctl --system >/dev/null

if [[ "$(sysctl -n vm.max_map_count)" -lt 262144 ]]; then
  fail "vm.max_map_count was not applied successfully."
fi

install -d -o root -g root -m 0750 "${DEPLOY_ROOT}"
install -d -o root -g root -m 0700 /var/backups/jobz
install -d -o root -g root -m 0700 /etc/jobz

ssm_active=false
for ssm_unit in amazon-ssm-agent.service snap.amazon-ssm-agent.amazon-ssm-agent.service; do
  if systemctl cat "${ssm_unit}" >/dev/null 2>&1; then
    systemctl enable --now "${ssm_unit}"
    if systemctl is-active --quiet "${ssm_unit}"; then
      ssm_active=true
      break
    fi
  fi
done

if [[ "${ssm_active}" != "true" ]]; then
  fail "AWS Systems Manager agent is not active. Fix it before relying on SSM-only access."
fi

# EC2 is administered through Session Manager. Keep the unused SSH daemon off.
systemctl disable --now ssh.service ssh.socket 2>/dev/null || true

printf '\n%s\n' 'Host bootstrap completed.'
printf 'Docker: %s\n' "$(docker --version)"
printf 'Compose: %s\n' "$(docker compose version)"
printf 'AWS CLI: %s\n' "$(aws --version 2>&1)"
printf 'Elasticsearch vm.max_map_count: %s\n' "$(sysctl -n vm.max_map_count)"
printf 'Deployment root: %s\n' "${DEPLOY_ROOT}"
printf '%s\n' 'Backup staging directory: /var/backups/jobz'
printf '%s\n' 'No application containers have been started.'
printf '%s\n' 'Use sudo for Docker administration; Docker group membership is intentionally not granted to human users.'
