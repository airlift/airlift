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

require 'launch'
require 'fileutils'

class Launcher < Launch::AbstractLauncher
  def initialize(file)
    super(file)

    @options[:jvm_arguments] = {}
    @options[:system_properties] = {}
    @options[:rack_environment] = 'production'
  end

  def add_custom_options(opts)
    opts.on('--config FILE', 'Defaults to INSTALL_PATH/etc/config.properties') do |v|
      @options[:config_path] = Pathname.new(v).expand_path
    end

    opts.on('--log-levels-file FILE', 'Defaults to INSTALL_PATH/etc/log.config') do |v|
      @options[:log_levels_path] = Pathname.new(v).expand_path
    end

    opts.on('--jvm-config FILE', 'Defaults to INSTALL_PATH/etc/jvm.config') do |v|
      @options[:jvm_config_path] = Pathname.new(v).expand_path
    end

    opts.on('--rack-environment ENVIRONMENT', 'Defaults to production') do |v|
      @options[:rack_environment] = v
    end

    opts.on('-D<name>=<value>', 'Sets a Java System property') do |v|
      key, value = v.split('=', 2).map(&:strip)
      raise 'Config can not be passed in a -D argument.  Use --config instead' if key == 'config'
      @options[:system_properties][key] = value
    end
  end

  def finalize_options
    @options[:environment]['RACK_ENV'] = @options[:rack_environment]
    @options[:environment]['RAILS_ENV'] = @options[:rack_environment]

    @options[:config_path] = File.join(@install_path, 'etc', 'config.properties')
    @options[:jvm_config_path] = File.join(@install_path, 'etc', 'jvm.config')
    @options[:log_levels_path] = File.join(@install_path, 'etc', 'log.config')
    @options[:rack_config] = File.join(@install_path, 'rack', 'config.ru')
    @options[:static_content] = File.join(@install_path, 'rack', 'public')

    @options[:jvm_arguments] = Launch::Properties.try_load_lines(@options[:jvm_config_path])

    @options[:jvm_properties] = @options[:node_properties].merge(@options[:system_properties])

    config_path = @options[:config_path]
    raise Launch::CommandError.new(:config_missing, "Config file is missing: #{config_path}") unless File.exists?(config_path)
  end

  def run_custom_setup
    src = File.join(@install_path, 'etc', '.')
    dest = File.join(@install_path, 'rack', 'config')
    FileUtils.cp_r(src, dest) if Dir.exist?(src)
  end

  def build_command_line(daemon)
    command = ['java']
    command += @options[:jvm_arguments]
    command += @options[:jvm_properties].map { |k, v| "-D#{k}=#{v}" }
    command <<= "-Dconfig=#{@options[:config_path]}"
    command <<= "-Dlog.output-file=#{@options[:log_path]}" if daemon
    command <<= "-Dlog.levels-file=#{@options[:log_levels_path]}" if File.exists?(@options[:log_levels_path])
    command <<= "-Drackserver.rack-config-path=#{@options[:rack_config]}" if File.exists?(@options[:rack_config])
    command <<= "-Drackserver.static-content-path=#{@options[:static_content]}" if Dir.exists?(@options[:static_content])
    command += ['-cp', File.join(@install_path, 'lib', '*')]
    command << 'com.proofpoint.rack.Main'
  end
end
