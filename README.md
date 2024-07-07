# AnarchyQueue

[![downloads](https://img.shields.io/github/downloads/zeroBzeroT/AnarchyQueue/total.svg?style=flat-square&labelColor=5c5c5c&color=007D9C)](https://github.com/zeroBzeroT/AnarchyQueue/releases/latest)
[![discord](https://img.shields.io/discord/843551077759844362?logo=discord)](https://discord.gg/7tW8ZAtGr5)
[![reddit](https://img.shields.io/reddit/subreddit-subscribers/0b0t)](https://old.reddit.com/r/0b0t/)
![last commit](https://img.shields.io/github/last-commit/zeroBzeroT/AnarchyQueue)
![repo size](https://img.shields.io/github/languages/code-size/zeroBzeroT/AnarchyQueue.svg?label=repo%20size)

A simple queue system for [Velocity](https://papermc.io/software/velocity) in the style of the 2b2t queue.

![logo](logo.png)

---

## Details

Connects players to a queue server instance when the main server is full or restarting. The player regularly receives
information about their position in the queue.
Use a plugin like [QueueServerPlugin](https://github.com/zeroBzeroT/QueueServerPlugin/) for the queue server instance.

## Statistics

![Graph](https://bstats.org/signatures/bungeecord/0b0t_AnarchyQueue.svg)

## Local Dev Environment

To start a local queue including velocity proxy and both paper servers, run the gradle tasks `runServerMain`, `runServerQueue` and `runVelocity`.

## Warranty

The Software is provided "as is" and without warranties of any kind, express
or implied, including but not limited to the warranties of merchantability,
fitness for a particular purpose, and non-infringement. In no event shall the
Authors or copyright owners be liable for any claims, damages or other
liability, whether in an action in contract, tort or otherwise, arising from,
out of or in connection with the Software or the use or other dealings in the
Software.
