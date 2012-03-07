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

if RUBY_VERSION < "1.9" || RUBY_ENGINE != "ruby"
  puts "MRI Ruby 1.9+ is required. Current version is #{RUBY_VERSION} [#{RUBY_PLATFORM}]"
  exit 99
end

require 'fileutils'
require 'optparse'
require 'pathname'
require 'uri'
require 'open-uri'
require 'json'

module Launch
  class Util
    # Find parent of bin/launcher.rb
    def self.find_install_path(file)
      path = Pathname.new(file)

      dir = path.dirname.expand_path
      base = dir.basename
      unless base.to_path == 'bin'
        raise "Expected file '#{file}' directory to be 'bin' not '#{base}'"
      end

      dir.parent.to_path
    end

    def self.strip_heredoc(string)
      space = /(\s+)/.match(string)[1]
      string.gsub(/^#{space}/, '')
    end

    def self.escape_shell_arg(string)
      if string =~ /[^\w.:\/-]/
        string = string.gsub("'", %q('\\\''))
        "'#{string}'"
      else
        string
      end
    end
  end


  class Properties
    # Load lines and strip comment lines
    def self.load_lines(path)
      File.open(path, 'r') do |f|
        f.readlines.map(&:strip).reject { |line| line.start_with?('#') }
      end
    end

    def self.try_load_lines(path)
      File.exists?(path) ? load_lines(path) : []
    end

    def self.load_properties(path)
      entries = load_lines(path).map do |line|
        line.split('=', 2).map(&:strip)
      end
      Hash[entries]
    end

    def self.try_load_properties(path)
      File.exists?(path) ? load_properties(path) : {}
    end
  end


  class ServiceInventory
    def initialize(options)
      url = options[:node_properties]['service-inventory.uri']
      raise 'No service-inventory.uri property in node.properties' unless url

      uri = URI.parse(url)
      data = uri.scheme == 'file' ? IO.read(uri.path) : uri.read

      json = JSON.parse(data)
      @services = json['services']
      raise 'No services field in service inventory' unless @services
    end

    def services(type, pool = nil)
      @services.find_all { |i| i['type'] == type && i['pool'] == pool }
    end

    def service(type, pool = nil)
      found = services(type, pool)
      name = type + (pool ? "/#{pool}" : '')
      raise "No services found for #{name}" if found.empty?
      raise "Found multiple services for #{name}" if found.length > 1
      found.first
    end

    def properties(type, pool = nil)
      service(type, pool)['properties'] || {}
    end
  end


  class Pid
    attr_reader :path

    def initialize(path, options = {})
      raise 'Path must be provided' unless path
      @path = path
      @verbose = options[:verbose]
    end

    def save(pid)
      Pathname.new(@path).dirname.mkpath
      File.open(@path, 'w') { |f| f.puts(pid) }
    end

    def clear
      File.delete(@path) if File.exists?(@path)
    end

    def alive?
      pid = get
      begin
        !pid.nil? && Process.kill(0, pid) == 1
      rescue Errno::ESRCH
        puts "Process #{pid} not running" if @verbose
        false
      rescue Errno::EPERM
        puts "Process #{pid} not visible" if @verbose
        false
      end
    end

    def get
      begin
        File.open(@path) { |f| f.read.to_i }
      rescue Errno::ENOENT
        puts "Can't find pid file #{@path}" if @verbose
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


  class Command
    def initialize(options, cmdline_builder)
      @options = options
      @cmdline_builder = cmdline_builder
      @pid = Pid.new(options[:pid_file])
      @verbose = options[:verbose]
    end

    def run
      command = @cmdline_builder.call(false)

      if @verbose
        puts command.join(' ')
        puts
      end

      Process.exec(@options[:environment],
                   *command,
                   :chdir => @options[:data_dir],
                   :umask => 0027,
                   :in => '/dev/null',
                   :close_others => true,
                   :unsetenv_others => true)
    end

    def start
      if @pid.alive?
        return :success, "Already running as #{@pid.get}"
      end

      command = @cmdline_builder.call(true)

      if @verbose
        puts command.join(' ')
        puts
      end

      Pathname.new(@options[:log_path]).dirname.mkpath

      pid = Process.spawn(@options[:environment],
                          *command,
                          :chdir => @options[:data_dir],
                          :umask => 0027,
                          :in => '/dev/null',
                          :out => [@options[:log_path], "a"],
                          :err => [:child, :out],
                          :unsetenv_others => true,
                          :pgroup => true)
      Process.detach(pid)

      @pid.save(pid)

      return :success, "Started as #{pid}"
    end

    def stop
      unless @pid.alive?
        @pid.clear
        return :success, "Stopped #{@pid.get}"
      end

      pid = @pid.get
      Process.kill('TERM', pid)

      sleep(0.1) while @pid.alive?

      @pid.clear

      return :success, "Stopped #{pid}"
    end

    def kill
      unless @pid.alive?
        @pid.clear
        return :success, "Killed #{@pid.get}"
      end

      pid = @pid.get
      Process.kill('KILL', pid)

      sleep(0.1) while @pid.alive?

      @pid.clear

      return :success, "Killed #{pid}"
    end

    def restart
      code, message = stop
      if code != :success then
        return code, message
      end
      start
    end

    def status
      if @pid.get.nil?
        return :not_running, 'Not Running'
      elsif @pid.alive?
        return :running, "Running as #{@pid.get}"
      else
        return :not_running_with_pid_file, "Program is dead and pid file #{@pid.path} exists"
      end
    end
  end


  class AbstractLauncher
    STATUS_CODES = {
      :success => 0,
      :running => 0,
      :not_running_with_pid_file => 1,
      :not_running => 3,
    }

    ERROR_CODES = {
      :generic_error => 1,
      :invalid_args => 2,
      :unsupported_command => 3,
      :config_missing => 6,
    }

    COMMANDS = [:start, :stop, :restart, :kill, :status, :run]

    BANNER = Util.strip_heredoc(<<-EOT)
      Usage: launcher [options] <command>

      Commands:
        start     run as daemon
        stop      stop gracefully
        restart   restart gracefully
        kill      hard stop
        status    check status of daemon
        run       run in foreground (for debugging)

      Options:
    EOT

    def initialize(file)
      @install_path = Util.find_install_path(file)

      @options = {
        :node_properties_path => File.join(@install_path, 'etc', 'node.properties'),
        :config_path => File.join(@install_path, 'etc', 'config.properties'),
        :data_dir => @install_path,
        :environment => {
          'PATH' => '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
        }
      }
    end

    def add_custom_options(opts)
    end

    def finalize_options
    end

    def run_custom_setup
    end

    def build_command_line(daemon)
      raise NotImplementedError
    end

    def execute(argv)
      option_parser = OptionParser.new(:unknown_options_action => :collect) do |opts|
        opts.banner = BANNER

        add_options(opts)
      end

      args = option_parser.permute(argv)

      node_properties = Properties.try_load_properties(@options[:node_properties_path])
      @options[:node_properties] = node_properties
      @options[:data_dir] = node_properties['node.data-dir'] if node_properties['node.data-dir']

      @options[:pid_file] ||= File.join(@options[:data_dir], 'var', 'run', 'launcher.pid')
      @options[:log_path] ||= File.join(@options[:data_dir], 'var', 'log', 'launcher.log')

      begin
        finalize_options
      rescue CommandError => e
        puts e.message
        puts e.code if @options[:verbose]
        exit ERROR_CODES[e.code]
      end

      if @options[:verbose]
        puts @options.map { |k, v| "#{k}=#{v}" }.join("\n")
        puts
      end

      begin
        run_custom_setup
      rescue CommandError => e
        puts e.message
        puts e.code if @options[:verbose]
        exit ERROR_CODES[e.code]
      end

      run(args)
    end

    def run(args)
      unless args.length == 1
        puts "Expected a single command, got '#{args.join(' ')}'"
        exit ERROR_CODES[:invalid_args]
      end

      command = args.first.to_sym

      unless COMMANDS.include? command
        puts "Unsupported command: #{command}"
        exit ERROR_CODES[:unsupported_command]
      end

      begin
        cmd = Command.new(@options, method(:build_command_line))
        code, message = cmd.send(command)
        puts message unless message.nil?
        exit STATUS_CODES[code]
      rescue CommandError => e
        puts e.message
        puts e.code if @options[:verbose]
        exit ERROR_CODES[e.code]
      end
    end

    def add_options(opts)
      opts.on('-v', '--verbose', 'Run verbosely') do |v|
        @options[:verbose] = v
      end

      opts.on('--node-config FILE', 'Defaults to INSTALL_PATH/etc/node.properties') do |v|
        @options[:node_properties_path] = File.expand_path(v)
      end

      opts.on('--data DIR', 'Defaults to node.data-dir') do |v|
        @options[:data_dir] = File.expand_path(v)
      end

      opts.on('--pid-file FILE', 'Defaults to DATA_DIR/var/run/launcher.pid') do |v|
        @options[:pid_file] = File.expand_path(v)
      end

      opts.on('--log-file FILE', 'Defaults to DATA_DIR/var/log/launcher.log (daemon only)') do |v|
        @options[:log_path] = File.expand_path(v)
      end

      add_custom_options(opts)

      opts.on('-h', '--help', 'Display this screen') do
        puts opts
        exit 2
      end
    end
  end
end
