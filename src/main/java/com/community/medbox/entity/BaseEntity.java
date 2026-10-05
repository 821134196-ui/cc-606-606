package com.community.medbox.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 实体基类：使用 SQLite 自增主键（IDENTITY）。
 * 不使用 PanacheEntity 默认的序列表生成器——它在单连接 SQLite 池下
 * 需要独立连接取序列值，会造成连接获取死锁。
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "<" + id + ">";
    }
}
