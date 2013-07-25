#
# Copyright 2010 Proofpoint, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'java'
require 'rubygems'

# Initialize the applications gem bundle
bundle_path = File.join(Dir.pwd, 'rack', 'bundle')
has_bundle = File.directory?(bundle_path)

if has_bundle
    puts 'Detected bundled gems at %s' % [bundle_path]
    $LOAD_PATH.unshift bundle_path
    require 'bundler/setup'
else
    puts 'No bundle detected at %s. Continuing without bundled gems.' % [bundle_path]
end

require 'rack'
require 'rack/rewindable_input'

