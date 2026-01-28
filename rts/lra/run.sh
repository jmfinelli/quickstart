#!/usr/bin/env bash
set -e
set -o pipefail
set -x
trap finish EXIT

function finish() {
  echo "Killing quarkus processes"
   for pid in "$ID1" "$ID2" "$ID3" "$ID4" "$ID5"; do
       [[ -z "$pid" || ! "$pid" =~ ^[0-9]+$ ]] && continue

       if kill -0 "$pid" 2>/dev/null; then
           kill -9 "$pid" 2>/dev/null || true
       fi
   done
}

urlencode() {
    # urlencode <string>
    old_lc_collate=$LC_COLLATE
    LC_COLLATE=C
    
    local length="${#1}"
    for (( i = 0; i < length; i++ )); do
        local c="${1:i:1}"
        case $c in
            [a-zA-Z0-9.~_-]) printf "$c" ;;
            *) printf '%%%02X' "'$c" ;;
        esac
    done
    
    LC_COLLATE=$old_lc_collate
}

start_service() {
  local http_port=$1
  local jar=$2
  shift 2

  java ${IP_OPTS} \
    -Dquarkus.http.port=$http_port \
    $(getDebugArgs $PORT) \
    "$@" \
    -jar "$jar" \
    >> "service-$http_port.log" 2>&1 &

  echo $!
}


function wait_for_all_coordinators() {
    echo "Waiting for coordinators to be ready..."

    COORDINATOR_PORTS=(9080 9081 9082 9083 9084)

    for i in {1..60}; do
        CHECK=0

        for port in "${COORDINATOR_PORTS[@]}"; do
            if curl -sf "http://localhost:${port}/q/health/ready" >/dev/null; then
                echo "Coordinator at ${port} ready"
                ((CHECK++))
            fi
        done

        if (( CHECK == ${#COORDINATOR_PORTS[@]} )); then
            echo "All coordinators are ready"
            break
        fi

        sleep 2
    done
}

function getDebugArgs {
    [ $DEBUG ] && echo "$JDWP"$1 || echo ""
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE=$(cd "$SCRIPT_DIR/../.." && pwd)
echo "WORKSPACE is set to: ${WORKSPACE}"

if [ ! -f $WORKSPACE/rts/lra-examples/coordinator-quarkus/target/lra-coordinator-quarkus-runner.jar ]; then
    echo "Please build first the lra-coordinator-quarkus module which is needed for this demo"
    exit -1
fi

export PORT=9787
export JDWP=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=

cd "$( dirname "${BASH_SOURCE[0]}" )"

CURL_IP_OPTS=""
IP_OPTS="${IPV6_OPTS}" # use setup of IPv6 if it's defined, otherwise go with IPv4
if [ -z "$IP_OPTS" ]; then
    IP_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv4Addresses"
    CURL_IP_OPTS="-4"
fi

ID1=$(start_service 9080 "$WORKSPACE/rts/lra-examples/coordinator-quarkus/target/lra-coordinator-quarkus-runner.jar")
((PORT++))

ID2=$(start_service 9081 "$WORKSPACE/rts/lra-examples/coordinator-quarkus/target/lra-coordinator-quarkus-runner.jar")
((PORT++))

ID3=$(start_service 9082 "hotel-service/target/quarkus-app/quarkus-run.jar" -Dlra.http.port=9080)
((PORT++))

ID4=$(start_service 9083 "flight-service/target/quarkus-app/quarkus-run.jar" -Dlra.http.port=9081)
((PORT++))

ID5=$(start_service 9084 "trip-controller/target/quarkus-app/quarkus-run.jar" -Dlra.http.port=9080)
((PORT++))


wait_for_all_coordinators

sleep 60

MAVEN_OPTS=${IP_OPTS} mvn -f trip-client/pom.xml exec:java -Dexec.args="confirm"
MAVEN_OPTS=${IP_OPTS} mvn -f trip-client/pom.xml exec:java -Dexec.args="cancel"

echo -e "\n\n\n"
BOOKINGID=$(curl ${CURL_IP_OPTS} -X POST "http://localhost:9084/?hotelName=TheGrand&flightNumber1=BA123&flightNumber2=RH456" -sS | jq -r ".id")
echo "Booking ID was: $BOOKINGID"


###### START not working
#When a coordinator killed and then restarted everything should keep working as usual
#instead when restarting the coordinator the final status of the nested LRAs is not correct
if [[ -n "$ID1" && "$ID1" =~ ^[0-9]+$ ]]; then
  if kill -0 "$ID1" 2>/dev/null; then
    echo "Killing PID $ID1"
    kill -9 "$ID1"
  else
    echo "Process $ID1 already exited"
  fi
fi
PORT=9787
ID1=$(start_service 9080 "$WORKSPACE/rts/lra-examples/coordinator-quarkus/target/lra-coordinator-quarkus-runner.jar")
########## END not working

wait_for_all_coordinators

sleep 60

echo -e "\n\n\n"

BOOKINGIDENCODED=$(urlencode "$BOOKINGID")
echo "Cancelling booking with ID: $BOOKINGIDENCODED"

MAX_RETRIES=6
SLEEP_TIME=10

for i in $(seq 1 $MAX_RETRIES); do
    echo "Attempt $i to cancel booking..."

    # Curl with timeout, fail early if HTTP error
    RESPONSE=$(curl ${CURL_IP_OPTS} -sS -f --connect-timeout 5 --max-time 10 \
        -X DELETE "http://localhost:9084/$BOOKINGIDENCODED") || true

    echo -e "\nResponse:\n$RESPONSE\n"

    # Skip parsing if response is empty or invalid
    STATUS=$(echo "$RESPONSE" | jq -r ".status" 2>/dev/null || echo "")

    if [ "$STATUS" = "CANCELLED" ]; then
        echo "Booking cancelled successfully!"
        break
    else
        echo "Status not CANCELLED yet: '$STATUS'"
    fi

    if [ $i -lt $MAX_RETRIES ]; then
        echo "Waiting $SLEEP_TIME seconds before retry..."
        sleep $SLEEP_TIME
    else
        echo "Failed to cancel booking after $MAX_RETRIES attempts"
        exit 1
    fi
done
  
if [ "$DEBUG" ]; then
    echo "Processes are still running ($ID1 $ID2 $ID3 $ID4 $ID5) press any key to end them"
    read
fi