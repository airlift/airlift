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


=begin

Options
* TODO install: invoked after package is deployed. Component should only make changes to resources it owns (i.e., no "touching" of environment or external services)
* TODO config
* TODO verify: make sure component can run within provided environment and with the given config.
* start (run as daemon) 
* stop (stop gracefully)    
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

require 'ftools'
require 'optparse'
require 'pathname'
require 'pp'

# loads lines and strips comments
def load_lines(file)
  File.open(file, 'r') do |f|
    f.readlines.
            map { |line| line.strip }.
            select { |line| line !~ /^(\s)*#/ }
  end
end

def load_properties(file)
  Hash[* load_lines(file).
          map { |line| k, v = line.split(/=/); [k, v] }.# need to make k, v explicit so that we can handle "key=". Otherwise, split returns a single element.
  flatten]
end

def strip(string)
  space = /(\s+)/.match(string)[1]
  string.gsub(/^#{space}/, '')
end

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
    File.delete(@path)
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

def build_cmd_line(options)
  install_path = Pathname.new(__FILE__).parent.parent

  log_option = if options[:daemon]
    "-Dlog.output-file=#{options[:log_path]}"
  else
    ""
  end

  log_levels_option = if File.exists?(options[:log_levels_path])
    "-Dlog.levels-file=#{options[:log_levels_path]}"
  else
    "" # ignore if levels file does not exist. TODO: should only ignore if using default & complain if user-provided file does not exist or has issues
  end

  config_path = options[:config_path]
  raise CommandError.new(:config_missing, "Config file is missing: #{config_path}") unless File.exists?(config_path)

  jvm_config_path = options[:jvm_config_path]
  raise CommandError.new(:config_missing, "JVM config file is missing: #{jvm_config_path}") unless File.exists?(jvm_config_path)

  jvm_properties = load_lines(jvm_config_path).join(' ')

  jar_path = File.join(install_path, 'lib', 'main.jar')

  command =<<-CMD
    java #{jvm_properties} -Dconfig=#{config_path} #{log_option} #{log_levels_option} -jar #{jar_path}
  CMD

  puts command if options[:verbose]

  command
end

def run(options)
  Dir.chdir(options[:install_path])
  exec(build_cmd_line(options))
end

def start(options)
  pid_file = Pid.new(options[:pid_file])
  if pid_file.alive?
    raise CommandError.new(:already_running, "Already running as #{pid_file.get}") unless pid_file.alive?
  end

  options[:daemon] = true
  command = build_cmd_line(options)
  
  pid = fork do
    Process.setsid
    Dir.chdir(options[:install_path])

    exec(command)
  end
  Process.detach(pid)

  pid_file.save(pid)

  return :success, "Started as #{pid}"
end

def stop(options)
  pid_file = Pid.new(options[:pid_file])

  raise CommandError.new(:not_running, "Not running") unless pid_file.alive?

  pid = pid_file.get
  Process.kill(Signal.list["TERM"], pid)

  while pid_file.alive? do
    sleep 0.1
  end

  pid_file.clear

  return :success, "Stopped #{pid}"
end

def kill(options)
  pid_file = Pid.new(options[:pid_file])

  raise CommandError.new(:not_running, "Not running") unless pid_file.alive?

  pid = pid_file.get

  Process.kill(Signal.list["KILL"], pid)

  while pid_file.alive? do
    sleep 0.1
  end

  pid_file.clear

  return :success, "Killed #{pid}"
end

def status(options)
  pid_file = Pid.new(options[:pid_file])
  
  if pid_file.alive?
    return :running, "Running as #{pid_file.get}"
  else
    return :not_running, "Not running"
  end
end

commands = [:run, :start, :stop, :kill, :status]
install_path = Pathname.new(__FILE__).parent.parent

# initialize defaults
options = {
        :pid_file => File.join(install_path, 'var', 'run', 'launcher.pid'),
        :jvm_config_path => File.join(install_path, 'etc', 'jvm.config'),
        :config_path => File.join(install_path, 'etc', 'config.properties'),
        :log_path => File.join(install_path, 'var', 'log', 'launcher.log'),
        :log_levels_path => File.join(install_path, 'etc', 'log.config'),
        :install_path => install_path
        }

option_parser = OptionParser.new do |opts|
  banner = <<-BANNER
    Usage: #{File.basename($0)} [options] <command>

    Commands:
      #{commands.join("\n  ")}

    Options:
  BANNER
  opts.banner = strip(banner)

  opts.on("-v", "--verbose", "Run verbosely") do |v|
    options[:verbose] = true
  end

  opts.on("--jvm-config FILE", "Defaults to INSTALL_PATH/etc/jvm.config") do |v|
    options[:jvm_config_path] = Pathname.new(v).expand_path
  end

  opts.on("--config FILE", "Defaults to INSTALL_PATH/etc/config.properties") do |v|
    options[:config_path] = Pathname.new(v).expand_path
  end

  opts.on("--pid-file FILE", "Defaults to INSTALL_PATH/var/run/launcher.pid") do |v|
    options[:pid_file] = Pathname.new(v).expand_path
  end

  opts.on("--log-file FILE", "Defaults to INSTALL_PATH/var/log/launcher.log (daemon only)") do |v|
    options[:log_path] = Pathname.new(v).expand_path
  end

  opts.on("--log-levels-file FILE", "Defaults to INSTALL_PATH/etc/log.config") do |v|
    options[:log_levels_path] = Pathname.new(v).expand_path
  end

  opts.on('-h', '--help', 'Display this screen') do
    puts opts
    exit 2
  end
end

option_parser.parse!(ARGV)

puts options.map { |k, v| "#{k}=#{v}"}.join("\n") if options[:verbose]

status_codes = {
        :success => 0,
        :running => 0,
        :not_running => 3
}


error_codes = {
        :generic_error => 1,
        :invalid_args => 2,
        :unsupported => 3,
        :config_missing => 6,
        :not_running => 7
}

if ARGV.length != 1
  puts option_parser
  puts
  puts "Expected a single command, got '#{ARGV.join(' ')}'"
  exit error_codes[:invalid_args]
end

command = ARGV[0].to_sym

unless commands.include?(command)
  puts option_parser
  puts
  puts "Unsupported command: #{command}"
  exit error_codes[:unsupported]
end

begin
  code, message = send(command, options)
  puts message unless message.nil?
  exit status_codes[code]
rescue CommandError => e
  puts e.message
  puts e.code if options[:verbose]
  exit error_codes[e.code]
end

