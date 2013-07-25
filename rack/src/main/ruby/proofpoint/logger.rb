module Proofpoint
  module RackServer
    class RackLogger
      def get_logger
        call_stack = caller(0)
        this_file = call_stack.first.split(/:\d+:in `/).first
        caller_call = call_stack.reject { |call| call =~ /#{this_file}\:|Forwardable/i }.first
        caller_name = File::basename(caller_call.split(/:\d+:in \`/).first) + ":" + caller_call.split(/:\d+:in `/).last.chomp("'")
        com::proofpoint::log.Logger.get(caller_name)
      end

      def debug(msg)
        get_logger.debug('%s', msg)
      end

      def info(msg)
        get_logger.info('%s', msg)
      end

      def warn(msg)
        get_logger.warn('%s', msg)
      end

      def error(msg)
        get_logger.error('%s', msg)
      end

      alias_method :fatal, :error

      def debug?
        get_logger.is_debug_enabled
      end

      def info?
        get_logger.is_info_enabled
      end

      # TODO: implement this method in platform logger
      def warn?
        true
      end

      alias_method :error?, :warn?
      alias_method :fatal?, :error?
    end
  end
end

