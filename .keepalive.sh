#!/bin/bash
# Keep session alive for 8 hours
# Writes a timestamp to a file so we know if we're still alive
echo "$(date +%s) keepalive" >> /tmp/keepalive.log
