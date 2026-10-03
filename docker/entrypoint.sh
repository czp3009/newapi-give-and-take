#!/bin/sh
set -eu

: "${NEW_API_BASE_URL:?NEW_API_BASE_URL is required.}"
: "${NEW_API_ACCESS_TOKEN:?NEW_API_ACCESS_TOKEN is required.}"
: "${TRACKING_FIELD:?TRACKING_FIELD is required.}"
: "${TRACKING_PATTERN:?TRACKING_PATTERN is required.}"

set -- /usr/local/bin/newapi-give-and-take --config /root/newapi-give-and-take/config.json --state /root/newapi-give-and-take/data/state.json

case "${RUN_MODE:-once}" in
    once)
        exec "$@"
        ;;
    cron)
        if [ -z "${CRON_SCHEDULE:-}" ]; then
            echo 'CRON_SCHEDULE is required when RUN_MODE=cron.' >&2
            exit 1
        fi
        if [ "$(printf '%s\n' "$CRON_SCHEDULE" | wc -w)" -ne 5 ]; then
            echo 'CRON_SCHEDULE must contain five cron fields, for example "* * * * *".' >&2
            exit 1
        fi
        runtime_directory=/root/newapi-give-and-take
        umask 077
        mkdir -p "$runtime_directory"
        # Cron resets its jobs' environment; preserve Docker settings in the private job script.
        {
            printf '#!/bin/sh\n'
            export -p
            printf 'exec %s >/proc/1/fd/1 2>/proc/1/fd/2\n' "$*"
        } > "$runtime_directory/job.sh"
        printf '%s /bin/sh %s/job.sh\n' "$CRON_SCHEDULE" "$runtime_directory" > "$runtime_directory/crontab"
        crontab "$runtime_directory/crontab"
        exec cron -f
        ;;
    *)
        echo 'RUN_MODE must be once or cron.' >&2
        exit 1
        ;;
esac
