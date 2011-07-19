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
* TODO install: invoked after package is deployed. Component should only make changes to resources it owns (i.e., no "touching" of environment or external services)
* TODO config
* TODO verify: make sure component can run within provided environment and with the given config.
* start (run as daemon)
* stop (stop gracefully)
* restart (restart gracefully)
* kill (hard stop)
* status (check status of daemon)

Custom commands (for dev & debugging convenience)
* run (run in foreground)

Expects config under "etc":
  jvm.config
  config.properties

--config to override config file
--jvm-config to override jvm config file

Logs to var/log/launcher.log when run as daemon
Logs to console when run in foreground, unless log file provided

Libs must be installed under "lib"

Requires java & ruby to be in PATH

=end

require 'fileutils'
require 'optparse'
require 'pathname'
require 'pp'

module Launcher
  STATUS_CODES = {
          :success => 0,
          :running => 0,
          :not_running_with_pid_file => 1,
          :not_running => 3
  }


  ERROR_CODES = {
          :generic_error => 1,
          :invalid_args => 2,
          :unsupported => 3,
          :config_missing => 6
  }

  class Pid
    def initialize(path, options = {})
      raise "Nil path provided" if path.nil?
      @options = options
      @path = path
    end

    def save(pid)
      Pathname.new(@path).parent.mkpath
      File.open(@path, "w") { |f| f.puts(pid) }
    end

    def clear()
      File.delete(@path) if File.exists?(@path)
    end

    def alive?
      pid = get
      begin
        !pid.nil? && Process.kill(0, pid) == 1
      rescue Errno::ESRCH
        puts "Process #{pid} not running" if @options[:verbose]
        false
      end
    end

    def get
      begin
        File.open(@path) { |f| f.read.to_i }
      rescue Errno::ENOENT
        puts "Can't find pid file #{@path}" if @options[:verbose]
      end
    end
  end

  class CommandError < RuntimeError
    attr_reader :code
    attr_reader :message
    def initialize(code, message)
      @code = code
      @message = message
    end
  end

  class CLI
    # loads lines and strips comments
    def self.load_lines(file)
      File.open(file, 'r') do |f|
        f.readlines.
                map { |line| line.strip }.
                select { |line| line !~ /^(\s)*#/ }
      end
    end

    def self.load_properties(file)
      Hash[* load_lines(file).
              map { |line| k, v = line.split(/=/); [k, v] }.# need to make k, v explicit so that we can handle "key=". Otherwise, split returns a single element.
      flatten]
    end

    def self.strip(string)
      space = /(\s+)/.match(string)[1]
      string.gsub(/^#{space}/, '')
    end

    def self.build_class_path(install_path)
      Dir.glob("#{install_path}/lib/*.jar", File::FNM_CASEFOLD).join(':')
    end

    def self.build_cmd_line(options)
      install_path = Pathname.new(__FILE__).parent.parent.expand_path

      config_path = options[:config_path]
      raise CommandError.new(:config_missing, "Config file is missing: #{config_path}") unless File.exists?(config_path)

      jvm_config_path = options[:jvm_config_path]
      raise CommandError.new(:config_missing, "JVM config file is missing: #{jvm_config_path}") unless File.exists?(jvm_config_path)

      class_path = build_class_path(install_path)

      command_parts = ["java"]
      command_parts += load_lines(jvm_config_path)
      command_parts += options[:system_properties]
      command_parts <<= "-Dconfig=#{config_path}"
      command_parts <<= "-Dlog.output-file=#{options[:log_path]}" if options[:daemon]
      command_parts <<= "-Dlog.levels-file=#{options[:log_levels_path]}" if File.exists?(options[:log_levels_path])
      command_parts <<= "-Drackserver.rack-config-path=#{options[:rack_config]}" if File.exists?(options[:rack_config])
      command_parts <<= "-Drackserver.static-content-path=#{options[:static_content]}" if Dir.exists?(options[:static_content])
      command_parts <<= "-cp '#{class_path}'"
      command_parts <<= "com.proofpoint.rack.Main"

      puts command_parts.join(' ') if options[:verbose]

      return command_parts.join(' ')
    end

    def self.copy_config()
      install_path = Pathname.new(__FILE__).parent.parent.expand_path

      FileUtils.cp_r "#{install_path}/etc/.", "#{install_path}/rack/config/" if Dir.exist? "#{install_path}/etc/"
    end

    def self.run(options)
      copy_config
      exec(options[:environment],
           build_cmd_line(options),
          :chdir=>options[:data_dir]
      )
    end

    def self.start(options)
      pid_file = Pid.new(options[:pid_file])
      if pid_file.alive?
        return :success, "Already running as #{pid_file.get}"
      end

      options[:daemon] = true
      command = build_cmd_line(options)

      copy_config

      puts command if options[:verbose]
      pid = spawn(options[:environment],
        command,
        :chdir => options[:data_dir],
        :out => "/dev/null",
        :err => "/dev/null"
      )
      Process.detach(pid)

      pid_file.save(pid)

      return :success, "Started as #{pid}"
    end

    def self.stop(options)
      pid_file = Pid.new(options[:pid_file])

      if !pid_file.alive?
        pid_file.clear
        return :success, "Stopped #{pid_file.get}"
      end

      pid = pid_file.get
      Process.kill(Signal.list["TERM"], pid)

      while pid_file.alive? do
        sleep 0.1
      end

      pid_file.clear

      return :success, "Stopped #{pid}"
    end

    def self.restart(options)
      code, message = stop(options)
      if code != :success then
        return code, message
      else
        start(options)
      end
    end

    def self.kill(options)
      pid_file = Pid.new(options[:pid_file])

      if !pid_file.alive?
        pid_file.clear
        return :success, "foo"
      end

      pid = pid_file.get

      Process.kill(Signal.list["KILL"], pid)

      while pid_file.alive? do
        sleep 0.1
      end

      pid_file.clear

      return :success, "Killed #{pid}"
    end

    def self.status(options)
      pid_file = Pid.new(options[:pid_file])

      if pid_file.get.nil?
        return :not_running, "Not running"
      elsif pid_file.alive?
        return :running, "Running as #{pid_file.get}"
      else
        # todo this is wrong. how do you get path from the pid_file
        return :not_running_with_pid_file, "Program is dead and pid file #{pid_file.get} exists"
      end
    end

    def self.parse_command_line(argv)
      commands = [:run, :start, :stop, :restart, :kill, :status]
      install_path = Pathname.new(__FILE__).parent.parent.expand_path

      # initialize defaults
      options = {
              :jvm_config_path => File.join(install_path, 'etc', 'jvm.config'),
              :config_path => File.join(install_path, 'etc', 'config.properties'),
              :data_dir => install_path,
              :log_levels_path => File.join(install_path, 'etc', 'log.config'),
              :install_path => install_path,
              :system_properties => [],
              :environment => {'RACK_ENV' => 'production', 'RAILS_ENV' => 'production'},
              :rack_config => File.join(install_path, 'rack', 'config.ru'),
              :static_content => File.join(install_path, 'rack', 'public')
              }

      option_parser = OptionParser.new(:unknown_options_action => :collect) do |opts|
        banner = <<-BANNER
          Usage: #{File.basename($0)} [options] <command>

          Commands:
            #{commands.join("\n  ")}

          Options:
        BANNER
        opts.banner = strip(banner)

        opts.on("--data DIR", "Defaults to INSTALL_PATH") do |v|
          options[:data_dir] = Pathname.new(v).expand_path
        end

        opts.on("-D<name>=<value>", "Sets a Java System property") do |v|
          if v.start_with?("config=") then
            raise("Config can not be passed in a -D argument.  Use --config instead")
          end
          options[:system_properties] << "-D#{v}"
        end

        opts.on("-v", "--verbose", "Run verbosely") do |v|
          options[:verbose] = true
        end

        opts.on('-h', '--help', 'Display this screen') do
          puts opts
          exit 2
        end
      end

      option_parser.parse!(argv)

      if options[:log_path].nil? then
        options[:log_path] =  File.join(options[:data_dir], 'var', 'log', 'launcher.log')
      end

      if options[:pid_file].nil? then
        options[:pid_file] =  File.join(options[:data_dir], 'var', 'run', 'launcher.pid')
      end

      puts options.map { |k, v| "#{k}=#{v}"}.join("\n") if options[:verbose]

      if argv.length != 1
        puts option_parser
        puts
        puts "Expected a single command, got '#{argv.join(' ')}'"
        exit ERROR_CODES[:invalid_args]
      end

      command = argv[0].to_sym

      unless commands.include?(command)
        puts option_parser
        puts
        puts "Unsupported command: #{command}"
        exit ERROR_CODES[:unsupported]
      end

      return command, options
    end

    def self.execute(argv)

      command, options = parse_command_line(argv)

      begin
        code, message = send(command, options)
        puts message unless message.nil?
        exit STATUS_CODES[code]
      rescue CommandError => e
        puts e.message
        puts e.code if options[:verbose]
        exit ERROR_CODES[e.code]
      end
    end
  end
end