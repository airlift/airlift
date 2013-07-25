# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = "rdoc"
  s.version = "3.12.2"

  s.required_rubygems_version = Gem::Requirement.new(">= 1.3") if s.respond_to? :required_rubygems_version=
  s.authors = ["Eric Hodel", "Dave Thomas", "Phil Hagelberg", "Tony Strauss"]
  s.cert_chain = ["-----BEGIN CERTIFICATE-----\nMIIDeDCCAmCgAwIBAgIBATANBgkqhkiG9w0BAQUFADBBMRAwDgYDVQQDDAdkcmJy\nYWluMRgwFgYKCZImiZPyLGQBGRYIc2VnbWVudDcxEzARBgoJkiaJk/IsZAEZFgNu\nZXQwHhcNMTIwMjI4MTc1NDI1WhcNMTMwMjI3MTc1NDI1WjBBMRAwDgYDVQQDDAdk\ncmJyYWluMRgwFgYKCZImiZPyLGQBGRYIc2VnbWVudDcxEzARBgoJkiaJk/IsZAEZ\nFgNuZXQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCbbgLrGLGIDE76\nLV/cvxdEzCuYuS3oG9PrSZnuDweySUfdp/so0cDq+j8bqy6OzZSw07gdjwFMSd6J\nU5ddZCVywn5nnAQ+Ui7jMW54CYt5/H6f2US6U0hQOjJR6cpfiymgxGdfyTiVcvTm\nGj/okWrQl0NjYOYBpDi+9PPmaH2RmLJu0dB/NylsDnW5j6yN1BEI8MfJRR+HRKZY\nmUtgzBwF1V4KIZQ8EuL6I/nHVu07i6IkrpAgxpXUfdJQJi0oZAqXurAV3yTxkFwd\ng62YrrW26mDe+pZBzR6bpLE+PmXCzz7UxUq3AE0gPHbiMXie3EFE0oxnsU3lIduh\nsCANiQ8BAgMBAAGjezB5MAkGA1UdEwQCMAAwCwYDVR0PBAQDAgSwMB0GA1UdDgQW\nBBS5k4Z75VSpdM0AclG2UvzFA/VW5DAfBgNVHREEGDAWgRRkcmJyYWluQHNlZ21l\nbnQ3Lm5ldDAfBgNVHRIEGDAWgRRkcmJyYWluQHNlZ21lbnQ3Lm5ldDANBgkqhkiG\n9w0BAQUFAAOCAQEAPeWzFnrcvC6eVzdlhmjUub2s6qieBkongKRDHQz5MEeQv4LS\nSARnoHY+uCAVL/1xGAhmpzqQ3fJGWK9eBacW/e8E5GF9xQcV3mE1bA0WNaiDlX5j\nU2aI+ZGSblqvHUCxKBHR1s7UMHsbz1saOmgdRTyPx0juJs68ocbUTeYBLWu9V4KP\nzdGAG2JXO2gONg3b4tYDvpBLbry+KOX27iAJulUaH9TiTOULL4ITJVFsK0mYVqmR\nQ8Tno9S3e4XGGP1ZWfLrTWEJbavFfhGHut2iMRwfC7s/YILAHNATopaJdH9DNpd1\nU81zGHMUBOvz/VGT6wJwYJ3emS2nfA2NOHFfgA==\n-----END CERTIFICATE-----\n"]
  s.date = "2013-02-25"
  s.description = "RDoc produces HTML and command-line documentation for Ruby projects.  RDoc\nincludes the +rdoc+ and +ri+ tools for generating and displaying online\ndocumentation.\n\nSee RDoc for a description of RDoc's markup and basic use."
  s.email = ["drbrain@segment7.net", "", "technomancy@gmail.com", "tony.strauss@designingpatterns.com"]
  s.executables = ["rdoc", "ri"]
  s.extra_rdoc_files = ["CVE-2013-0256.rdoc", "DEVELOPERS.rdoc", "History.rdoc", "LEGAL.rdoc", "LICENSE.rdoc", "Manifest.txt", "README.rdoc", "RI.rdoc", "TODO.rdoc", "bin/rdoc", "Rakefile"]
  s.files = ["bin/rdoc", "bin/ri", "CVE-2013-0256.rdoc", "DEVELOPERS.rdoc", "History.rdoc", "LEGAL.rdoc", "LICENSE.rdoc", "Manifest.txt", "README.rdoc", "RI.rdoc", "TODO.rdoc", "Rakefile"]
  s.homepage = "http://docs.seattlerb.org/rdoc"
  s.post_install_message = "Depending on your version of ruby, you may need to install ruby rdoc/ri data:\n\n<= 1.8.6 : unsupported\n = 1.8.7 : gem install rdoc-data; rdoc-data --install\n = 1.9.1 : gem install rdoc-data; rdoc-data --install\n>= 1.9.2 : nothing to do! Yay!\n"
  s.rdoc_options = ["--main", "README.rdoc"]
  s.require_paths = ["lib"]
  s.required_ruby_version = Gem::Requirement.new(">= 1.8.7")
  s.rubyforge_project = "rdoc"
  s.rubygems_version = "1.8.15"
  s.summary = "RDoc produces HTML and command-line documentation for Ruby projects"

  if s.respond_to? :specification_version then
    s.specification_version = 4

    if Gem::Version.new(Gem::VERSION) >= Gem::Version.new('1.2.0') then
      s.add_runtime_dependency(%q<json>, ["~> 1.4"])
      s.add_development_dependency(%q<minitest>, ["~> 4.3"])
      s.add_development_dependency(%q<rdoc>, ["~> 3.10"])
      s.add_development_dependency(%q<racc>, ["~> 1.4"])
      s.add_development_dependency(%q<ZenTest>, ["~> 4"])
      s.add_development_dependency(%q<hoe>, ["~> 3.5"])
    else
      s.add_dependency(%q<json>, ["~> 1.4"])
      s.add_dependency(%q<minitest>, ["~> 4.3"])
      s.add_dependency(%q<rdoc>, ["~> 3.10"])
      s.add_dependency(%q<racc>, ["~> 1.4"])
      s.add_dependency(%q<ZenTest>, ["~> 4"])
      s.add_dependency(%q<hoe>, ["~> 3.5"])
    end
  else
    s.add_dependency(%q<json>, ["~> 1.4"])
    s.add_dependency(%q<minitest>, ["~> 4.3"])
    s.add_dependency(%q<rdoc>, ["~> 3.10"])
    s.add_dependency(%q<racc>, ["~> 1.4"])
    s.add_dependency(%q<ZenTest>, ["~> 4"])
    s.add_dependency(%q<hoe>, ["~> 3.5"])
  end
end
