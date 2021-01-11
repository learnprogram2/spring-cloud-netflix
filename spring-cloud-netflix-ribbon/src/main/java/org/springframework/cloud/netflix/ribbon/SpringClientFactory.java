/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.ribbon;

import java.lang.reflect.Constructor;

import com.netflix.client.IClient;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import org.springframework.beans.BeanUtils;
import org.springframework.cloud.context.named.NamedContextFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * A factory that creates client, load balancer and client configuration instances. It
 * creates a Spring ApplicationContext per client name, and extracts the beans that it
 * needs from there.
 *
 * @author Spencer Gibb
 * @author Dave Syer
 *
 * 这个factory主要创建 client, load-balancer还有clientConfiguration.
 * 每个服务(eurekaClient注册的服务)都创建于给SpringApplicationContext, 从里面拿需要的.
 *
 *1.  {@link NamedContextFactory} 这个context, 可以理解成一个map, 里面<eurekaServiceName, SpringApplicationContext> 这样的key-v
 * 来达成我们的目的 为每个eurekaService(一个eureka源码里的application的scope) 创建一个AnnotationConfigApplicationContext
 *2. {@link RibbonClientConfiguration} 这个是spring的配置类
 * 这个设计太牛逼了
 */
public class SpringClientFactory extends NamedContextFactory<RibbonClientSpecification> {

	static final String NAMESPACE = "ribbon";

	// 使用RibbonClientConfiguration这个配置类, 然后本SpringCLientFactory的namespace是ribbon(暂时没有发现用处),
	// RibbonClientConfiguration 就是每个eurekaService对应的springApplicationContext的配置噢!!!!
	public SpringClientFactory() {
		super(RibbonClientConfiguration.class, NAMESPACE, "ribbon.client.name");
	}

	/**
	 * Get the rest client associated with the name.
	 * @param name name to search by
	 * @param clientClass the class of the client bean
	 * @param <C> {@link IClient} subtype
	 * @return {@link IClient} instance
	 * @throws RuntimeException if any error occurs
	 */
	public <C extends IClient<?, ?>> C getClient(String name, Class<C> clientClass) {
		return getInstance(name, clientClass);
	}

	/**
	 * Get the load balancer associated with the name.
	 * @param name name to search by
	 * @return {@link ILoadBalancer} instance
	 * @throws RuntimeException if any error occurs
	 */
	public ILoadBalancer getLoadBalancer(String name) {
		return getInstance(name, ILoadBalancer.class);
	}

	/**
	 * Get the client config associated with the name.
	 * @param name name to search by
	 * @return {@link IClientConfig} instance
	 * @throws RuntimeException if any error occurs
	 */
	public IClientConfig getClientConfig(String name) {
		return getInstance(name, IClientConfig.class);
	}

	/**
	 * Get the load balancer context associated with the name.
	 * @param serviceId id of the service to search by
	 * @return {@link RibbonLoadBalancerContext} instance
	 * @throws RuntimeException if any error occurs
	 */
	public RibbonLoadBalancerContext getLoadBalancerContext(String serviceId) {
		return getInstance(serviceId, RibbonLoadBalancerContext.class);
	}

	static <C> C instantiateWithConfig(Class<C> clazz, IClientConfig config) {
		return instantiateWithConfig(null, clazz, config);
	}

	static <C> C instantiateWithConfig(AnnotationConfigApplicationContext context,
			Class<C> clazz, IClientConfig config) {
		C result = null;

		try {
			Constructor<C> constructor = clazz.getConstructor(IClientConfig.class);
			result = constructor.newInstance(config);
		}
		catch (Throwable e) {
			// Ignored
		}

		if (result == null) {
			result = BeanUtils.instantiateClass(clazz);

			if (result instanceof IClientConfigAware) {
				((IClientConfigAware) result).initWithNiwsConfig(config);
			}

			if (context != null) {
				context.getAutowireCapableBeanFactory().autowireBean(result);
			}
		}

		return result;
	}
	// 这里执行拿: 从我们为eurekaClient注册的服务分配的springContext里面拿到type这个bean.
	@Override
	public <C> C getInstance(String name, Class<C> type) {
		C instance = super.getInstance(name, type);
		if (instance != null) {
			return instance;
		}
		IClientConfig config = getInstance(name, IClientConfig.class);
		return instantiateWithConfig(getContext(name), type, config);
	}

	// 这里是拿到context, 用eureka注册的服务名字
	@Override
	protected AnnotationConfigApplicationContext getContext(String name) {
		return super.getContext(name);
	}

}
