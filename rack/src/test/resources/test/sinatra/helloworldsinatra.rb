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

require 'sinatra'
require 'json'

get "/name-echo" do
  logger = request.logger
  logger.debug "name-echo was called with #{params[:name]}"
  return "#{params[:name]}"
end

post "/temp-store" do
  @@tmp = request.body.read
  status 201
  return ''
end

get "/temp-store" do
  return @@tmp
end

get "/header-cookies-json" do
  return [200, {'Content-Type' => 'application/json'}, request.cookies.to_json]
end


class ClosableResponse
  def initialize
    $closed = false
  end

  def each(&callback)
    ['hello'].each(&callback)
  end

  def close
    $closed = true
  end
end

get "/closable-response" do
  return [200, ClosableResponse.new]
end

get "/close-called" do
  return $closed.to_s
end
