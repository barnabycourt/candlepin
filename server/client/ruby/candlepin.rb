require 'cgi'
require 'forwardable'
require 'httpclient'
require 'json'
require 'date'
require 'openssl'
require 'uri'

# JSONClient stuff is courtesy of the jsonclient.rb example in the httpclient
# repo
module HTTP
  class Message
    # Returns JSON object of message body
    alias original_content content
    def content
      if JSONClient::CONTENT_TYPE_JSON_REGEX =~ content_type
        JSON.parse(original_content)
      else
        original_content
      end
    end
  end
end

# JSONClient provides JSON related methods in addition to HTTPClient.
class JSONClient < HTTPClient
  CONTENT_TYPE_JSON_REGEX = /(application|text)\/(x-)?json/i

  attr_accessor :content_type_json

  class JSONRequestHeaderFilter
    attr_accessor :replace

    def initialize(client)
      @client = client
      @replace = false
    end

    def filter_request(req)
      req.header['content-type'] = @client.content_type_json if @replace
    end

    def filter_response(req, res)
      @replace = false
    end
  end

  def initialize(*args)
    super
    @header_filter = JSONRequestHeaderFilter.new(self)
    @request_filter << @header_filter
    @content_type_json = 'application/json; charset=utf-8'
  end

  # Takes in a uri, either a hash or individual arguments, and a block
  # If a hash is given then the body, query string, headers, etc. are just
  # passed through to HTTPClient's post.  The block is always passed through untouched
  #
  # If individual arguments are given they must be provided in the following order:
  #   body
  #   query
  #   header
  #   follow_redirect
  #
  # And any trailing nil arguments can be omitted.
  #
  # Examples:
  # json_body = {...}
  # query_args = {...}
  # post('/path', json_body)
  # post('/path', json_body, query_args)
  # post('/path', json_body, query_args, {'Accept' => 'text/html'})
  # post('/path', :query => query_args, :body => json_body)

  def post(uri, *args, &block)
    @header_filter.replace = true
    request(:post, uri, jsonify(argument_to_hash(args, :body, :query, :header, :follow_redirect)), &block)
  end

  # See documentation for post
  def put(uri, *args, &block)
    @header_filter.replace = true
    request(:put, uri, jsonify(argument_to_hash(args, :body, :query, :header)), &block)
  end

private

  def jsonify(hash)
    if !hash.nil? && hash.key?(:body)
      hash[:body] = JSON.generate(hash[:body]) unless hash[:body].nil?
    end
    hash
  end
end

module Candlepin
  module Util
    # Converts a string or symbol to a camel case string or symbol
    def camel_case(s)
      conversion = s.to_s.split('_').inject([]) do |buffer, e|
        buffer.push(buffer.empty? ? e : e.capitalize)
      end.join

      if s.is_a?(Symbol)
        conversion.to_sym
      else
        conversion
      end
    end

    # Convert all keys to camel case.
    def camelize_hash(h, *args)
      h = select_from(h, *args) unless args.nil? || args.empty?
      camelized = h.each.map do |entry|
        [camel_case(entry.first), entry.last]
      end
      Hash[camelized]
    end

    # Create a subset of key-value pairs.  This method yields the subset and the
    # original hash to a block so that developers can easily add or
    # tweak the resultant hash.  For example:
    #     h = {:hello => 'world', :goodbye => 'bye' }
    #     select_from(h, :hello) do |subset, original|
    #       h[:send_off] = original[:goodbye].upcase
    #     end
    def select_from(hash, *args, &block)
      missing = args.flatten.reject { |key| hash.key?(key) }
      unless missing.empty?
        raise ArgumentError.new("Missing keys: #{missing}")
      end

      pairs = args.map do |key|
          hash.assoc(key)
      end
      h = Hash[pairs]
      yield h, hash if block_given?
      h
    end

    # Verify that a hash supplied as the first argument contains only keys specified
    # by subsequent arguments.  The purpose of this method is to help developers catch
    # mistakes and typos in the option hashes supplied to methods.  Suppose a method
    # expects to receive a hash with a ":name" key in it and the user sends in a hash with
    # the key ":nsme".  Ordinarily that would be accepted and the incorrect key would be
    # silently ignored.  However, calling verify_keys(hash, :name) would raise an error
    # to alert the developer to the mistake.
    #
    # The valid_keys argument can either be a single array of valid keys or the valid keys
    # listed inline.  For example:
    #     verify_keys(hash, defaults.keys)
    #     verify_keys(hash, :name, :rank, :serial_number)
    def verify_keys(hash, *valid_keys)
      keys = Set.new(hash.keys)
      valid_keys = Set.new(valid_keys.flatten)
      unless keys == valid_keys || keys.proper_subset?(valid_keys)
        extra = keys.difference(valid_keys)
        msg = "Hash #{hash} contains invalid keys: #{extra.to_a}"
        raise RuntimeError.new(msg)
      end
    end

    def verify_and_merge(opts, defaults)
      verify_keys(opts, defaults.keys)
      defaults.merge(opts)
    end
  end

  module API
    # I am attempting to follow strict rules with this API module:
    #  * API calls with one parameter can use a normal Ruby method parameter.
    #  * API calls with more than one parameter MUST use an options hash.
    #  * Methods with options hashes should provide reasonable defaults and
    #    merge those defaults with the provided options in a manner similar to
    #      defaults = {:some_parameter => 'sensible default'}
    #      opts = defaults.merge(opts)
    #  * Do NOT use Ruby 2.0 style keyword arguments. This API should remain 1.9.3
    #    compatible.
    #  * All keys in options hashes MUST be symbols.
    #  * Methods SHOULD generally follow these conventions:
    #      - If request is a GET, method begins with get_
    #      - If request is a DELETE, method begins with delete_
    #      - If request is a POST, method begins with create_, add_, or post_
    #      - If request is a PUT, method begins with update_ or put_
    #      - Aliases are acceptable, but use alias_method instead of just alias
    #  * URL construction should be performed with the Ruby URI class and/or the
    #    to_query methods added to Object, Array, and Hash.  No ad hoc string manipulations.

    # TODO At some point it might make more sense to set up some AOP advice at
    # the "before method call" joinpoint around defining, merging, and
    # validating the default options.  (The Aquarium gem seems to be a good fit)
    # E.g.
    #
    # req_defaults :username => nil, :password => nil
    # def create_user(opts)
    #   do stuff here
    # end

    def self.included(klass)
      # Mixin the Util module's methods into this module
      klass.class_eval do
        include Util
      end
      # Automatically include child Modules
      # This type of meta-programming is a little too complicated for my taste,
      # but the API has so many methods that it becomes difficult to navigate the
      # file if the methods aren't grouped into logical sections.
      klass.constants.each do |sym|
        klass.class_eval do
          include const_get(sym) if const_get(sym).kind_of?(Module)
        end
      end

    end

    attr_writer :uuid
    def uuid
      return @uuid || nil
    end

    module ConsumerResource
      def register(opts = {})
        defaults = {
          :name => nil,
          :type => :system,
          :uuid => uuid,
          :facts => {},
          :username => nil,
          :owner => nil,
          :activation_keys => [],
          :installed_products => [],
          :environment => nil,
          :capabilities => [],
          :hypervisor_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        consumer_json = select_from(opts, :name, :facts, :uuid) do |h|
          if opts[:hypervisor_id]
            h[:hypervisorId] = {
              :hypervisorId => opts[:hypervisor_id],
            }
          end

          unless opts[:capabilities].empty?
            h[:capabilities] = opts[:capabilities].map do |c|
              Hash[:name, c]
            end
          end
        end

        consumer_json = {
          :type => { :label => opts[:type] },
          :installedProducts => opts[:installed_products],
        }.merge(consumer_json)

        if opts[:environment].nil?
          path = "/consumers"
        else
          path = "/environments/#{opts[:environment]}/consumers"
        end

        query_args = select_from(opts, :username, :owner)
        keys = opts[:activation_keys].join(",")
        query_args[:activation_keys] = keys unless keys.empty?

        post(path, :query => query_args, :body => consumer_json)
      end

      def delete_deletion_record(opts = {})
        defaults = {
          :deleted_uuid => nil,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/consumers/#{opts[:deleted_uuid]}/deletionrecord"
        delete(path)
      end

      def update_consumer(opts = {})
        defaults = {
          :uuid => uuid,
          :facts => {},
          :installed_products => [],
          :hypervisor_id => nil,
          :guest_ids => [],
          :autoheal => true,
          :service_level => nil,
          :capabilities => [],
        }
        opts = verify_and_merge(opts, defaults)

        body = opts.dup

        body[:capabilities].map! do |name|
          { :name => name }
        end

        body[:guest_ids].map! do |id|
          { :guestId => id }
        end

        body = camelize_hash(body)
        path = "/consumers/#{opts[:uuid]}"
        put(path, body)
      end

      def update_all_guest_ids(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_ids => [],
        }
        opts = verify_and_merge(opts, defaults)

        path = "/consumers/#{opts[:uuid]}/guestids"
        body = opts[:guest_ids].map do |id|
            { :guestId => id }
        end
        put(path, body)
      end

      def update_guest_id(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}"
        put(path, camelize_hash(opts, :guest_id))
      end

      def get_all_guest_ids(opts = {})
        defaults = {
          :uuid => uuid,
        }
        opts = verify_and_merge(opts, defaults)

        get("/consumers/#{opts[:uuid]}/guestids")
      end

      def get_guest_id(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_id => nil,
        }
        opts = verify_and_merge(opts, defaults)
        get("/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}")
      end

      def delete_guest_id(opts = {})
        defaults = {
          :uuid => uuid,
          :guest_id => nil,
          :unregister => false,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/consumers/#{opts[:uuid]}/guestids/#{opts[:guest_id]}"
        delete(path, select_from(opts, :unregister))
      end
    end

    module HypervisorResource
      def post_hypervisor_check_in(opts = {})
        defaults = {
           :owner => nil,
           :host_guest_mapping => {},
           :create_missing => nil,
         }
         opts = verify_and_merge(opts, defaults)

         body = opts[:host_guest_mapping]
         post('/hypervisors', :query => select_from(opts, :owner, :create_missing), :body => body)
      end
    end

    module DeletedConsumerResource
      def get_deleted_consumers(opts = {})
        defaults = {
          :date => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get('/deleted_consumers')
      end
    end

    module EntitlementResource
      def update_entitlement(opts = {})
        defaults = {
          :id => nil,
          :quantity => 1,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/entitlements/#{opts[:id]}"
        put(path, opts)
      end

      def update_entitlement_consumer(opts = {})
        defaults = {
          :id => nil,
          :to_consumer => nil,
          :quantity => 1,
        }
        opts = verify_and_merge(opts, defaults)

        path = "/entitlements/#{opts[:id]}"
        put(path, select_from(opts, :to_consumer, :quantity))
      end
    end

    module UserResource
      def create_user(opts = {})
        defaults = {
          :username => nil,
          :password => nil,
          :super_admin => false,
        }
        opts = verify_and_merge(opts, defaults)
        post("/users", camelize_hash(opts))
      end

      def update_user(opts = {})
        defaults = {
          :username => nil,
          :password => nil,
          :super_admin => false,
        }
        opts = verify_and_merge(opts, defaults)
        put("/users/#{opts[:username]}", camelize_hash(opts))
      end

      def get_user(opts = {})
        defaults = {
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)
        get("/users/#{opts[:username]}")
      end

      def get_user_roles(opts = {})
        defaults = {
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)
        get("/users/#{opts[:username]}/roles")
      end

      def get_user_owners(opts = {})
        defaults = {
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)
        get("/users/#{opts[:username]}/owners")
      end

      def delete_user(opts = {})
        defaults = {
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)
        delete("/users/#{opts[:username]}")
      end

      def get_all_users
        get('/users')
      end
    end

    module RoleResource
      def create_role(opts = {})
        defaults = {
          :name => nil,
          :permissions => [],
        }
        opts = verify_and_merge(opts, defaults)

        post("/roles", opts)
      end

      def update_role(opts = {})
        defaults = {
          :role_id => nil,
          :users => [],
          :permissions => [],
          :name => nil,
        }
        opts = verify_and_merge(opts, defaults)

        put("/roles/#{opts[:role_id]}", opts)
      end

      def get_role(opts = {})
        defaults = {
          :role_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        get("/roles/#{opts[:role_id]}")
      end

      def delete_role(opts = {})
        defaults = {
          :role_id => nil,
        }
        opts = verify_and_merge(opts, defaults)

        delete("/roles/#{opts[:role_id]}")
      end

      def add_role_user(opts = {})
        defaults = {
          :role_id => nil,
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)

        post("/roles/#{opts[:role_id]}/users/#{opts[:username]}")
      end

      def delete_role_user(opts = {})
        defaults = {
          :role_id => nil,
          :username => nil,
        }
        opts = verify_and_merge(opts, defaults)

        delete("/roles/#{opts[:role_id]}/users/#{opts[:username]}")
      end

      def add_role_permission(opts = {})
        defaults = {
          :role_id => nil,
          :permission => nil,
        }
        opts = verify_and_merge(opts, defaults)

        permission = select_from(opts, :owner, :access)
        post("/roles/#{opts[:role_id]}/permissions/#{opts[:permission_id]}", permission)
      end

      def delete_role_permission(opts = {})
        defaults = {
          :role_id => nil,
          :permission_id => nil,
          :owner => nil,
          :access => 'READ_ONLY',
        }
        opts = verify_and_merge(opts, defaults)

        delete("/roles/#{opts[:role_id]}/permissions/#{opts[:permission_id]}")
      end
    end

    module OwnerResource
      def get_all_owners
        get('/owners')
      end
    end
  end

  class NoAuthClient
    include Candlepin::API

    extend Forwardable
    # By extending Forwardable we can simply take useful HTTPClient methods
    # and make them available and then for the implementation we just pass
    # everything through to @client
    #
    # HTTPClient has many methods, but the below seemed like the most useful.
    def_delegators :@client,
      :debug_dev=,
      :delete,
      :delete_async,
      :get,
      :get_async,
      :get_content,
      :post,
      :post_async,
      :post_content,
      :post_content_async,
      :put,
      :put_async,
      :head,
      :options,
      :request,
      :request_async,
      :trace

    attr_accessor :use_ssl
    attr_accessor :host
    attr_accessor :port
    attr_accessor :context
    attr_accessor :insecure
    attr_accessor :ca_path
    attr_accessor :connection_timeout
    attr_accessor :client

    # Build a connection without any authentication
    #
    # = Options
    # * :host:: The host to connect to. Defaults to localhost
    # * :port:: The port to connect to. Defaults to 8443.
    #     Should be provided as an integer.
    # * :context:: The servlet context to use. Defaults to 'candlepin'.
    #     If you do not provide a leading slash, one will be prepended.
    # * :use_ssl:: Whether to connect over SSL/TLS. Defaults to true.
    # * :insecure:: Whether to perform SSL hostname verification and whether to
    #     require a recognized CA. Defaults to <b>true</b> because in testing we
    #     are often dealing with self-signed certificates.
    # * :connection_timeout:: How long in seconds to wait before the connection times
    #     out. Defaults to <b>3 seconds</b>.
    def initialize(opts = {})
      defaults = {
        :host => 'localhost',
        :port => 8443,
        :context => '/candlepin',
        :use_ssl => true,
        :insecure => true,
        :connection_timeout => 3,
      }
      opts = defaults.merge(opts)
      # Subclasses must provide an attr_writer or attr_accessor for every key
      # in the options hash.  The snippet below sends the values to the setter methods.
      opts.each do |k, v|
        self.send(:"#{k}=", v)
      end
      reload
    end

    def context=(val)
      @context = val
      if !val.nil? && val[0] != '/'
        @context = "/#{val}"
      end
    end

    def debug
      self.debug=(true)
    end

    def debug=(value)
      if value
        client.debug_dev = $stdout
      else
        client.debug_dev = nil
      end
    end

    # Create a new HTTPClient. Useful after making configuration changes through the
    # accessors.
    def reload
      @client = raw_client
    end

    # Return the base URL that the Client is using.  Consists of protocol, host, port, and context.
    def base_url
      components = {
        :host => host,
        :port => port,
        :path => context,
      }
      if use_ssl
        uri = URI::HTTPS.build(components)
      else
        uri = URI::HTTP.build(components)
      end
      uri.to_s
    end

    # This method provides the raw HTTPClient object that is being used
    # to communicate with the server.  In most circumstances, you should not
    # need to access it, but it is there if you need it.
    def raw_client
      client = JSONClient.new(:base_url => base_url)
      # Three seconds is the default and that is pretty aggressive, but this code is mainly
      # meant for spec tests and we don't want to wait all day for connections to timeout
      # if something is wrong.
      client.connect_timeout = connection_timeout
      if use_ssl
        if insecure
          client.ssl_config.verify_mode = OpenSSL::SSL::VERIFY_NONE
        else
          client.ssl_config.add_trust_ca(ca_path) if ca_path
        end
      end
      client
    end
  end

  class X509Client < NoAuthClient
    attr_accessor :client_cert
    attr_accessor :client_key

    class << self
      def from_consumer(consumer_json, opts = {})
        if opts.key?(:client_cert) || opts.key?(:client_key)
          raise ArgumentError.new("Cannot specify cert and key for this method")
        end
        client_cert = OpenSSL::X509::Certificate.new(consumer_json['idCert']['cert'])
        client_key = OpenSSL::PKey::RSA.new(consumer_json['idCert']['key'])
        opts = {
          :client_cert => client_cert,
          :client_key => client_key,
        }.merge(opts)
        X509Client.new(opts)
      end

      def from_files(cert, key, opts = {})
        if opts.key?(:client_cert) || opts.key?(:client_key)
          raise ArgumentError.new("Cannot specify cert and key for this method")
        end
        client_cert = OpenSSL::X509::Certificate.new(File.read(cert))
        client_key = OpenSSL::PKey::RSA.new(File.read(key))
        opts = {
          :client_cert => client_cert,
          :client_key => client_key,
        }.merge(opts)
        X509Client.new(opts)
      end
    end

    # Build a connection using an X509 certificate provided as a client certificate
    #
    # = Options
    # * Same as those for NoAuthClient
    # * :client_cert:: An OpenSSL::X509::Certificate object. Defaults to nil.
    # * :client_key:: An OpenSSL::PKey::PKey object. Defaults to nil.
    def initialize(opts = {})
      defaults = {
        :client_cert => nil,
        :client_key => nil,
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      client.ssl_config.client_cert = client_cert
      client.ssl_config.client_key = client_key
      client
    end
  end

  class OAuthClient < NoAuthClient
    def initialize(opts = {})
      defaults = {
        :oauth_key => nil,
        :oauth_secret => nil,
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      # TODO OAuth stuff here
      client
    end
  end

  class BasicAuthClient < NoAuthClient
    attr_accessor :username
    attr_accessor :password

    # Build a connection using HTTP basic authentication.
    #
    # = Options
    # * Same as those for NoAuthClient
    # * :username:: The username to use. Defaults to 'admin'.
    # * :password:: The password to use. Defaults to 'admin'.
    def initialize(opts = {})
      defaults = {
        :username => 'admin',
        :password => 'admin',
      }
      opts = defaults.merge(opts)
      super(opts)
    end

    def raw_client
      client = super
      client.force_basic_auth = true
      client.set_auth(base_url, username, password)
      client
    end
  end
end
