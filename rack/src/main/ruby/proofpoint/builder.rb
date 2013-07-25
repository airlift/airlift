module Proofpoint
  module RackServer
    class Builder
      def initialize
        @logger = com::proofpoint::log.Logger.get('Proofpoint::RackServer::Builder')
      end

      def build(filename)
        rack_app, options_ignored = Rack::Builder.parse_file filename
        return ServletAdapter.new(rack_app)
      end
    end
  end
end

