require 'rails/railtie'

module Proofpoint
  module RackServer
    class Railtie < Rails::Railtie
      initializer 'proofpoint.rack_server.initialize_logger', :before => :initialize_logger do
        Rails.logger = RackLogger.new
      end
    end
  end
end
