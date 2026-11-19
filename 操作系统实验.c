#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/module.h>

static int __init hello_world_init(void)
{
	pr_info("hello,world\n");
	return 0;
}

static void __exit hello_world_exit(void)
{
	pr_info("goodbye\n");
}

module_init(hello_world_init);
module_exit(hello_world_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("Linux Kernel Module Lab");
MODULE_DESCRIPTION("Print a message when the module is loaded and unloaded");
