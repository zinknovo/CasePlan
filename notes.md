### 1. 需求分析

精读需求，自己想业务逻辑的问题、需求澄清的问题，然后再问ai

e.g. 功能优先级、edge case定义（如重复检测）、error处理方式

怎么问ai: 1.上下文（技术栈、业务背景） 2.输出格式（代码/解释？） 3.分步骤

关键维度：数据关系、边界定义、who&what



### 2. MVP

walkthrough（走查）：想清楚有几个api，每个是什么，怎么用

mvp要素：前后端，数据库，不需要异步、MQ、MVC等

bonus功能：下载、搜索

llm调用流程：拼prompt->组http请求->用rest模板发post请求->解析响应



### 3. 数据库

为什么要用api操作数据库：1.安全 2.方便验证 3.处理并发 4.可扩展

数据库设计思路：识别实体->确定属性->选定主键->建立关系（外键放“多”方）



### 4. 消息队列

三大考量点：时效性、可靠性、效率；轮询有时差无法“立刻”，且会空转；线程池程序崩会丢任务；PG notify错过通知任务就没了



### 5. Worker&微服务

模式：pull v. push(aws)

问题点：轮询时间、没任务/任务过多怎么处理、失败策略（最大重试次数、指数退避）、崩溃处理（取任务、调api、orphan recovery)

微服务好处：独立扩展/部署，故障隔离；坏处：通信复杂，调试变难



### 6.更新前端

前端轮询/后端SSE，轮询可以分阶段，先快后慢。轮询实现简单，但容易浪费请求，SSE更节约资源，但实现复杂。



### 7.controller拆分

响应处理层+数据格式转换层+业务逻辑层，配合数据库层的repo+entity

为什么这样分：单一职责、方便测试/复用/维护，解耦controller和数据库

数据格式变化：前端发送JSON字符串，通过http变成请求体，spring反序列化成java对象，传参给service，service dto后创建entity(db orm)传参给repo，同时塞入消息队列，待worker执行完毕后，然后返回caseplan entity，序列化成响应体返回JSON给前端



### 8.报错，警告与测试

|                    | 系统标识匹配       | 系统标识不匹配    |
| :----------------- | :----------------- | ----------------- |
| **自然标识匹配**   | ① 完全一样 → 复用  | ② 可疑 → 警告     |
| **自然标识不匹配** | ③ 矛盾 → 阻止/警告 | ④ 完全不同 → 放行 |

400: 请求有问题，服务器没法处理，会阻塞

409: 请求和服务器数据冲突，会阻塞

200: 请求成功了，但有些警告，确认后可以继续

201: 请求成功，正常提交

2xx: 成功 3xx: 重定向 4xx: 客户端问题 5xx: 服务器问题

Error: 必须阻止，不能继续，得先修复，比如同一执业号对应不同人名

Warning: 可以继续，但需要用户确认，比如同一身份证但不同姓名

各种异常统一字段、统一处理，减少重复逻辑

请求的数据格式变化流程：前端发json给后端，spring反序列化为java对象然后处理，响应时如果成功返回java对象，失败时返回map，但最后发到网络上都会再序列化成json

集成测试：验证flow，模拟用户行为，涵盖多个模块

Q：如果两个agent在改同一个项目，冲突了怎么办？测试如果用ai写，我怎么知道测试得够不够？只看覆盖率吗？对测试需要了解到什么程度？



### 9&10.设计模式

数据源处理：adapter+工厂类

LLM服务处理：@Component用在类上，@Bean用在方法上

包层级划分：web, core, integration, common



### 11.监控

系统挂了：需要看错误日志（比如哪个请求）、服务状态

系统慢：耗时分解、资源使用率（CPU、内存、磁盘、网络）、流量情况

费用上涨：API调用统计、infra费用

自动告警：自动发现、自动通知、自动恢复

上述场景统称为可观测性，分logs(日志)/metrics(指标)/traces(链路)三大支柱

actuator开窗 micrometer翻译 prometheus记录 grafana画图

![image-20260214130404991](/Users/Z1nk/Library/Application Support/typora-user-images/image-20260214130404991.png)



### 12.AWS基础

SQS：simple queue service，会主动送任务

Lambda: 无服务器函数计算，功能上可以理解为随用版EC2

RDS: relational databse service，aws的数据库

ENI: Elastic Network Service

Q：AWS概念太多，直接用codex/cc+aws cli代操作了，是不是只要理解概念/跑通就行？



### 13.Lambda化的重构、部署和测试

六边形组织：业务逻辑在中间，不依赖任何外部技术。外部技术通过"端口"和"适配器"接入。

本spring boot项目的lambda化就是把use case做成core，然后周围一圈用port。详见architect.md

Q: lambda化之后测试怎么做？针对接口？单元测试和集成测试分别怎么做？aws信赖部署？

A: 单元测试和集成测试都只测共享业务逻辑，在本地跑；Handler 薄壳不测；AWS 基础设施信赖平台；部署后做轻量冒烟验证胶水层。

![image-20260214184246588](/Users/Z1nk/Library/Application Support/typora-user-images/image-20260214184246588.png)

p.s. 重构代码没亲自看，需要熟悉到什么程度？



### 14.死信队列

一条消息如果失败超过n次就扔到私信队列不再处理，修好bug后再回来处理

进DLQ后处理：分类失败原因，修复后退回主队列，并配合CW设置告警，保证消费逻辑幂等，避免重复处理

更具体：按错误类型分流，网络/限流直接重试，权限/配置先修环境，数据/代码先修再重放，整个过程靠 DLQ 消息主键 + CloudWatch 堆栈定位



### 15.Terraform(IaC)

需要配置aws (cli)凭证

前端放s3+cloudfront



### 16.api开发流程（见docs/playbook）



### 17.CI/CD

github cli + aws cli Dynamodb做分布式锁以处理同时写入

这块没太搞懂，直接用coding agent + cli做了
