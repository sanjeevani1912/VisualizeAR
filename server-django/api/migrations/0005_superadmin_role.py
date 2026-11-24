from django.db import migrations, models

ROLES = ['planner', 'reviewer', 'admin', 'auditor', 'superadmin']


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0004_user_must_change_password'),
    ]

    operations = [
        migrations.RemoveConstraint(model_name='user', name='user_role_valid'),
        migrations.AlterField(
            model_name='user',
            name='role',
            field=models.CharField(choices=[(r, r) for r in ROLES], max_length=10),
        ),
        migrations.AddConstraint(
            model_name='user',
            constraint=models.CheckConstraint(condition=models.Q(('role__in', ROLES)), name='user_role_valid'),
        ),
    ]
