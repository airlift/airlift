#!/usr/bin/env ruby
#
# Copyright 2010 Proofpoint, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if RUBY_VERSION < "1.9" || RUBY_ENGINE != "ruby" then
  puts "MRI Ruby 1.9+ is required. Current version is #{RUBY_VERSION} [#{RUBY_PLATFORM}]"
  exit 99
end

=begin

Options
* start (run as daemon)
* stop (stop gracefully)
* restart (restart gracefully)
* try-restart (restart gracefully if already running)
* force-reload (cause the configuration of the service to be reloaded -- same as try-restart)
* kill (hard stop)
* status (check status of daemon)

Expects config under "etc":
  node.properties
  jvm.config
  config.properties

--config to override config file
--jvm-config to override jvm config file

Logs to var/log/launcher.log when run as daemon
Logs to console when run in foreground, unless log file provided

Libs must be installed under "lib"

Requires java & ruby to be in PATH

=end

require 'pathname'

install_path = Pathname.new(__FILE__).parent.parent.expand_path
exec("java", "-jar", "#{install_path}/lib/launcher.jar", *ARGV)
