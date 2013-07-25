module Proofpoint
  module RackServer
    class ServletAdapter
      def initialize(app)
        @app = app
        @logger = RackLogger.new
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
          'PATH_INFO' => servlet_request.request_uri,
          'QUERY_STRING' => (servlet_request.query_string || ""),
          'SERVER_NAME' => servlet_request.server_name,
          'SERVER_PORT' => servlet_request.server_port.to_s
        }

        rack_env['CONTENT_TYPE'] = servlet_request.content_type unless servlet_request.content_type.nil?
        rack_env['CONTENT_LENGTH']  = servlet_request.content_length unless servlet_request.content_length.nil?

        servlet_request.header_names.reject { |name| name =~ /^Content-(Type|Length)$/i }.each do |name|
          rack_env["HTTP_#{name.upcase.gsub(/-/,'_')}"] = servlet_request.get_headers(name).to_a.join(',')
        end

        response_status, response_headers, response_body = @app.call(rack_env)

        servlet_response.status = response_status.to_i
        response_headers.each do |header_name, header_value|
          case header_name
            when /^Content-Type$/i
              servlet_response.content_type = header_value.to_s
            when /^Content-Length$/i
              servlet_response.content_length = header_value.to_i
            when /^Set-Cookie$/i
              header_value.to_s.split("\n").each{|value| servlet_response.add_header(header_name.to_s, value)}
            else
              servlet_response.add_header(header_name.to_s, header_value.to_s)
          end
        end

        response_stream = servlet_response.output_stream
        response_body.each { |part| response_stream.write(part.to_java_bytes) }
        response_stream.flush rescue nil

      ensure
        response_body.close if response_body.respond_to? :close
      end
    end

  end
end

