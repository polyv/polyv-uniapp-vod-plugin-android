该插件封装了保利威 Android 与 iOS 原生点播 SDK，集成了保利威常用的基本接口。使用本插件可以轻松把保利威 Android 与 iOS SDK 集成到自己的 app 中，实现保利威视频播放、下载等功能。想要集成本插件，需要在[保利威视频云平台](http://www.polyv.net/)注册账号，并开通相关服务。

## 开发环境

uni-app 开发环境：HBuilderX 2.8.0+

## 前提条件

1. [保利威官网](http://www.polyv.net/)账号

## 集成打包说明

###通用插件集成云打包

1. 购买插件，选择该插件绑定的项目。
2. 在HBuilderX里找到项目，在manifest的app原生插件配置中勾选模块，如需要填写参数则参考插件作者的文档添加。
3. 根据插件作者的提供的文档开发代码，在代码中引用插件，调用插件功能。
4. 打包[自定义基座](https://ask.dcloud.net.cn/article/35115)，选择插件，得到自定义基座，然后运行时选择自定义基座，进行log输出测试。
5. 开发完毕后正式云打包

### HBuilderx 本地插件打包集成

Android 原生插件在 HBuilderx 中支持云打包。同时开发者也可以下载插件，以本地插件包的方式，打正式包或者自定义基座。

将下载的插件包复制到前端工程的`nativeplugins`目录下，然后在`manifest.json`文件的【App原生插件配置】项下点击【选择本地插件】，在列表中选择需要打包生效的插件。确定保存后，重新提交云端打包生效。

详细配置方式可以参考官方文档：[uni-app原生插件本地配置](https://nativesupport.dcloud.net.cn/NativePlugin/use/use_local_plugin?id=uni-app%e5%8e%9f%e7%94%9f%e6%8f%92%e4%bb%b6%e6%9c%ac%e5%9c%b0%e9%85%8d%e7%bd%ae)。

### Android 离线打包集成

Android 原生离线打包参考官方文档：[uni-app原生插件集成指南](https://nativesupport.dcloud.net.cn/NativePlugin/offline_package/android?id=uni-app原生插件集成指南)。

## 快速使用

### 播放器plv-player

播放器控件为 component，仅允许在 nvue 中声明使用。详细接口使用说明查看插件包里的README文档。

```html
		<plv-player ref="vod" seekType=0 autoPlay=true disableScreenCAP=false rememberLastPosition=false @onPlayStatus="onPlayStatus" @onPlayError="onPlayError" @positionChange="positionChange" 
		style="background-color:#333333; height:300px;position:fixed; top:0px; left:0px; right:0px;">
		</plv-player>

//js方法，播放vid视频
setVid(){
  this.$refs.vod.setVid({
    vid:'c538856dde5bf6a7419dfeece53f83af_c',
    level:0
  },
  (ret) => {
    if (ret.errMsg != null) {
      uni.showToast({
        title: ret.errMsg,
        icon: "none"
      })
    }
  })
},

```

### 基本模块

通过 uni.requireNativePlugin("ModuleName") 获取 module ，就可以直接调用相关模块接口。目前有三个模块如下代码所示。其中ConfigModule用于点播的初始化，开发者必须在[保利威官网](http://www.polyv.net/)注册以后，在点播后台获取到配置信息初始化，播放器才能正式使用。项目模块介绍和接口说明查看插件包里的README文档。

```html
	var configModule = uni.requireNativePlugin("PLV-VodUniPlugin-ConfigModule")
	var infoModule = uni.requireNativePlugin("PLV-VodUniPlugin-InfoModule")
	var downloadModule = uni.requireNativePlugin("PLV-VodUniPlugin-DownloadModule")

//...
	configModule.setToken({
			'userid': '',
			'readtoken': '',
			'writetoken': '',
			'secretkey': ''
		},
		(ret) => {
			if (ret.isSuccess == true) {
				uni.showToast({
					title:'设置token成功',
					icon: "none"
				})
			} else {
				let errMsg = ret.errMsg;
				uni.showToast({
					title:'设置token失败：' + errMsg,
					icon: "none"
				})
			}
		})

```





