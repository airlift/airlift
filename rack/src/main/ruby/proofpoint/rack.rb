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

require 'rubygems'
require 'rack'
require 'rack/rewindable_input'
require 'forwardable'

module Proofpoint
  module RackServer
    class Builder
      def build(filename, logger)
        rack_app, options_ignored = Rack::Builder.parse_file filename
        return ServletAdapter.new(rack_app, logger)
      end
    end

    class ServletAdapter
      def initialize(app, logger)
        @app = app
        @logger = RackLogger.new(logger)
        @errors = java::lang::System::err.to_io # TODO: write to logger
      end

      def call(servlet_request, servlet_response)
        rack_env = {
                'rack.version' => Rack::VERSION,
                'rack.multithread' => true,
                'rack.multiprocess' => false,
                'rack.input' => Rack::RewindableInput.new(servlet_request.input_stream.to_io),
                'rack.errors' => @errors,
                'rack.logger' => @logger,
                'rack.url_scheme' => servlet_request.scheme,
                'REQUEST_METHOD' => servlet_request.method,
                'SCRIPT_NAME' => '',
                'PATH_INFO' => servlet_request.path_info,
                'QUERY_STRING' => (servlet_request.query_string || ""),
                'SERVER_NAME' => servlet_request.server_name,
                'SERVER_PORT' => servlet_request.server_port.to_s
        }

        rack_env['CONTENT_TYPE'] = servlet_request.content_type unless servlet_request.content_type.nil?
        rack_env['CONTENT_LENGTH']  = servlet_request.content_length unless servlet_request.content_length.nil?

        # header_names is an Enumeration, so this sucks, but it's the lesser of all evils imho.
        request_header_names = servlet_request.header_names
        unless request_header_names.nil?
          while request_header_names.has_more_elements
            header_name = request_header_names.next_element
            unless header_name =~ /^Content-(Type|Length)$/i
              header_values = servlet_request.get_headers(header_name)
              header_value_array = Array.new
              while header_values.has_more_elements
                header_value_array.push(header_values.next_element)
              end
              rack_env["HTTP_#{header_name.upcase.gsub(/-/,'_')}"] = header_value_array.join(',')
            end
          end
        end

        response_status, response_headers, response_body = @app.call(rack_env)

        servlet_response.status = response_status
        response_headers.each do |header_name, header_value|
          case header_name
            when /^Content-Type$/i
              servlet_response.content_type = header_value.to_s
            when /^Content-Length$/i
              servlet_response.content_length = header_value.to_i
            else
              header_value.to_s.split("\n").each { |split_value| servlet_response.add_header(header_name.to_s, split_value) }
          end
        end

        response_stream = servlet_response.output_stream
        response_body.each { |part| response_stream.write(part.to_java_bytes) }
        response_stream.flush
      end
    end

    class RackLogger

      extend Forwardable

      def_delegators :@logger,:debug,:info,:warn,:error

      alias_method :fatal,:error

      def initialize(logger)
        @logger = logger
      end
    end
  end
end
